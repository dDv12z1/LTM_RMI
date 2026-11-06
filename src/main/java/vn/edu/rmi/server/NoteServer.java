package vn.edu.rmi.server;

import vn.edu.rmi.shared.Note;
import vn.edu.rmi.shared.NoteService;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public class NoteServer extends UnicastRemoteObject implements NoteService {
    private static final long serialVersionUID = 1L;

    private final String serverId;
    private final String serverName;
    private final Path dataFile;
    private final String peerHost;
    private final int peerPort;
    private final Map<String, Note> notes = new HashMap<>();
    private long clock = 0;
    private volatile boolean peerOnline = false;
    private volatile String peerName = "";
    private boolean peerTried = false;
    private volatile String lastPeerError = "";
    private volatile long lastSyncTime = 0;
    private transient volatile Consumer<String> logListener;
    private final AtomicLong clientChanges = new AtomicLong();
    private final AtomicLong receivedChanges = new AtomicLong();
    private final AtomicLong successfulSyncs = new AtomicLong();

    public NoteServer(String serverId, int servicePort, Path dataFile,
                      String peerHost, int peerPort) throws Exception {
        this(serverId, serverId, servicePort, dataFile, peerHost, peerPort);
    }

    public NoteServer(String serverId, String serverName, int servicePort, Path dataFile,
                      String peerHost, int peerPort) throws Exception {
        super(servicePort); // Cố định cổng RMI để hai máy dễ mở firewall.
        this.serverId = serverId;
        this.serverName = serverName;
        this.dataFile = dataFile;
        this.peerHost = peerHost;
        this.peerPort = peerPort;
        try {
            loadFromFile();
        } catch (Exception e) {
            UnicastRemoteObject.unexportObject(this, true);
            throw e;
        }
    }

    // GUI dùng các hàm này để hiển thị tình trạng đồng bộ.
    public boolean isPeerOnline() { return peerOnline; }
    public String getPeerName() { return peerName; }
    public String getLastPeerError() { return lastPeerError; }
    public long getLastSyncTime() { return lastSyncTime; }
    public long getClientChanges() { return clientChanges.get(); }
    public long getReceivedChanges() { return receivedChanges.get(); }
    public long getSuccessfulSyncs() { return successfulSyncs.get(); }
    public void setLogListener(Consumer<String> listener) { this.logListener = listener; }

    private void log(String message) {
        System.out.println(message);
        Consumer<String> listener = logListener;
        if (listener != null) listener.accept(message);
    }

    @Override
    public String getServerName() {
        return serverName;
    }

    @Override
    public synchronized List<Note> listNotes() {
        List<Note> result = new ArrayList<>();
        for (Note note : notes.values()) {
            if (!note.isDeleted()) result.add(note);
        }
        result.sort(Comparator.comparing(Note::getId));
        return result;
    }

    @Override
    public Note addNote(String text) throws RemoteException {
        checkText(text);
        Note note;
        synchronized (this) {
            note = new Note(UUID.randomUUID().toString(), text.trim(), ++clock, serverId, false);
            notes.put(note.getId(), note);
            saveToFile();
        }
        clientChanges.incrementAndGet();
        log("Đã thêm ghi chú " + note.getId());
        sendToPeer(note);
        return note;
    }

    @Override
    public Note editNote(String id, String text) throws RemoteException {
        checkText(text);
        Note note;
        synchronized (this) {
            Note old = notes.get(id);
            if (old == null || old.isDeleted()) throw new RemoteException("Ghi chú không còn tồn tại.");
            note = new Note(id, text.trim(), ++clock, serverId, false);
            notes.put(id, note);
            saveToFile();
        }
        clientChanges.incrementAndGet();
        log("Đã sửa ghi chú " + note.getId());
        sendToPeer(note);
        return note;
    }

    @Override
    public void deleteNote(String id) throws RemoteException {
        Note note;
        synchronized (this) {
            Note old = notes.get(id);
            if (old == null || old.isDeleted()) return;
            // Giữ bản ghi đã xóa để server vừa kết nối lại cũng biết việc xóa.
            note = new Note(id, old.getText(), ++clock, serverId, true);
            notes.put(id, note);
            saveToFile();
        }
        clientChanges.incrementAndGet();
        log("Đã xóa ghi chú " + note.getId());
        sendToPeer(note);
    }

    @Override
    public synchronized List<Note> getAllNotes() {
        return new ArrayList<>(notes.values());
    }

    @Override
    public synchronized void receiveNote(Note incoming) throws RemoteException {
        if (incoming == null || incoming.getId() == null || incoming.getServerId() == null) {
            throw new RemoteException("Dữ liệu đồng bộ không hợp lệ.");
        }
        clock = Math.max(clock, incoming.getVersion());
        Note current = notes.get(incoming.getId());
        if (isNewer(incoming, current)) {
            notes.put(incoming.getId(), incoming);
            saveToFile();
            receivedChanges.incrementAndGet();
            log("Nhận đồng bộ từ server " + incoming.getServerId() + ": " + incoming.getId());
        }
    }

    // Số phiên bản tăng dần trên mỗi server. Nếu bằng nhau, ID server quyết định.
    private boolean isNewer(Note incoming, Note current) {
        if (current == null) return true;
        if (incoming.getVersion() != current.getVersion()) {
            return incoming.getVersion() > current.getVersion();
        }
        return incoming.getServerId().compareTo(current.getServerId()) > 0;
    }

    private void checkText(String text) throws RemoteException {
        if (text == null || text.isBlank()) throw new RemoteException("Nội dung không được trống.");
    }

    private void sendToPeer(Note note) {
        try {
            Registry registry = LocateRegistry.getRegistry(peerHost, peerPort);
            NoteService peer = (NoteService) registry.lookup(SERVICE_NAME);
            peer.receiveNote(note);
        } catch (Exception e) {
            // Vẫn nhận thao tác tại server này; vòng đồng bộ sẽ gửi lại sau.
            log("Chưa gửi được sang peer: " + e.getMessage());
        }
    }

    /** Chạy định kỳ: lấy dữ liệu từ peer rồi gửi lại dữ liệu mình có. */
    public void syncWithPeer() {
        try {
            Registry registry = LocateRegistry.getRegistry(peerHost, peerPort);
            NoteService peer = (NoteService) registry.lookup(SERVICE_NAME);
            String connectedPeerName = peer.getServerName();
            for (Note note : peer.getAllNotes()) receiveNote(note);
            for (Note note : getAllNotes()) peer.receiveNote(note);
            peerName = connectedPeerName;
            if (!peerOnline) log("Đã kết nối và đồng bộ với " + peerName + ".");
            peerOnline = true;
            peerTried = true;
            lastPeerError = "";
            lastSyncTime = System.currentTimeMillis();
            successfulSyncs.incrementAndGet();
        } catch (Exception e) {
            lastPeerError = e.getMessage();
            if (!peerTried || peerOnline) log("Chưa kết nối với server bên kia: " + e.getMessage());
            peerTried = true;
            peerOnline = false;
        }
    }

    @SuppressWarnings("unchecked")
    private synchronized void loadFromFile() throws Exception {
        if (!Files.exists(dataFile)) return;
        try (ObjectInputStream in = new ObjectInputStream(Files.newInputStream(dataFile))) {
            List<Note> saved = (List<Note>) in.readObject();
            for (Note note : saved) {
                notes.put(note.getId(), note);
                clock = Math.max(clock, note.getVersion());
            }
        }
    }

    private synchronized void saveToFile() throws RemoteException {
        try {
            Files.createDirectories(dataFile.toAbsolutePath().getParent());
            Path temp = dataFile.resolveSibling(dataFile.getFileName() + ".tmp");
            try (ObjectOutputStream out = new ObjectOutputStream(Files.newOutputStream(temp))) {
                out.writeObject(new ArrayList<>(notes.values()));
            }
            Files.move(temp, dataFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            throw new RemoteException("Không lưu được dữ liệu vào " + dataFile, e);
        }
    }
}
