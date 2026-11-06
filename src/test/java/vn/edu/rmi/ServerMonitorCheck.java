package vn.edu.rmi;

import vn.edu.rmi.server.NoteServer;
import vn.edu.rmi.shared.Note;
import vn.edu.rmi.shared.NoteService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.rmi.Remote;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

/** Kiểm tra hai server RMI và số liệu mà giao diện server hiển thị. */
public class ServerMonitorCheck {
    public static void main(String[] args) throws Exception {
        System.setProperty("java.rmi.server.hostname", "127.0.0.1");
        Path folder = Files.createTempDirectory(Path.of("target"), "server-check-");
        NoteServer a = null;
        NoteServer b = null;
        Registry registryA = null;
        Registry registryB = null;
        try {
            a = new NoteServer("127.0.0.1:1099", "Server máy 1", 1100, folder.resolve("a.ser"), "127.0.0.1", 2099);
            registryA = LocateRegistry.createRegistry(1099);
            registryA.rebind(NoteService.SERVICE_NAME, a);
            b = new NoteServer("127.0.0.1:2099", "Server máy 2", 2100, folder.resolve("b.ser"), "127.0.0.1", 1099);
            registryB = LocateRegistry.createRegistry(2099);
            registryB.rebind(NoteService.SERVICE_NAME, b);

            a.syncWithPeer();
            b.syncWithPeer();
            NoteService remoteA = (NoteService) LocateRegistry.getRegistry("127.0.0.1", 1099)
                    .lookup(NoteService.SERVICE_NAME);
            Note note = a.addNote("Kiểm tra giao diện server");
            b.syncWithPeer();

            check(a.isPeerOnline() && b.isPeerOnline(), "Peer chưa kết nối");
            check(a.getPeerName().equals("Server máy 2") && b.getPeerName().equals("Server máy 1"),
                    "Tên peer chưa hiển thị đúng");
            check(remoteA.getServerName().equals("Server máy 1"), "Tên server chưa trả qua RMI");
            check(b.listNotes().stream().anyMatch(n -> n.getId().equals(note.getId())), "Ghi chú chưa sang B");
            check(a.getClientChanges() == 1, "Sai số thao tác client");
            check(b.getReceivedChanges() == 1, "Sai số bản nhận từ peer");
            check(a.getSuccessfulSyncs() > 0 && b.getSuccessfulSyncs() > 0, "Sai số lần đồng bộ");
            check(a.getLastSyncTime() > 0, "Thiếu thời gian đồng bộ");

            unexport(registryB);
            registryB = null;
            unexport(b);
            b = null;
            a.syncWithPeer();
            check(!a.isPeerOnline(), "Server chưa báo mất kết nối khi peer dừng");

            System.out.println("OK: tên server qua RMI, đồng bộ và trạng thái kết nối đều đúng.");
        } finally {
            unexport(registryA);
            unexport(registryB);
            unexport(a);
            unexport(b);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void unexport(Remote remote) {
        if (remote == null) return;
        try { UnicastRemoteObject.unexportObject(remote, true); }
        catch (Exception ignored) { }
    }
}
