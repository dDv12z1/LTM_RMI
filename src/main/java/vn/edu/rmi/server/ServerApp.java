package vn.edu.rmi.server;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;
import vn.edu.rmi.shared.Note;
import vn.edu.rmi.shared.NoteService;

import java.nio.file.Path;
import java.rmi.Remote;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Mở một cửa sổ này trên mỗi máy server. */
public class ServerApp extends Application {
    private final TextField nameField = new TextField();
    private final TextField localAddress = new TextField("127.0.0.1:1099");
    private final TextField peerAddress = new TextField("127.0.0.1:2099");
    private final Button startButton = new Button("Chạy server");
    private final Button stopButton = new Button("Dừng server");
    private final Label status = new Label("Nhập hai địa chỉ IP:Port rồi bấm Chạy server.");

    private final Label localState = new Label("Chưa chạy");
    private final Label localDetail = new Label("Địa chỉ: --");
    private final Label localRmiPort = new Label("Cổng RMI: --");
    private final Label peerState = new Label("Chưa kết nối");
    private final Label peerName = new Label("Tên: chưa biết");
    private final Label peerDetail = new Label("Địa chỉ: --");
    private final Label peerError = new Label("");
    private final Label lastSync = new Label("Lần đồng bộ: chưa có");
    private final Label noteCount = new Label("0 ghi chú");
    private final Label dataFileLabel = new Label("File dữ liệu: --");
    private final Label clientChanges = new Label("0 thao tác client");
    private final Label receivedChanges = new Label("0 bản nhận từ peer");
    private final Label syncCount = new Label("0 lần đồng bộ");
    private final ObservableList<Note> notesOnScreen = FXCollections.observableArrayList();
    private final ListView<Note> noteList = new ListView<>(notesOnScreen);
    private final TextArea logArea = new TextArea();

    private NoteServer server;
    private Registry registry;
    private ScheduledExecutorService scheduler;
    private Timeline monitor;
    private Stage stage;

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        System.setProperty("sun.rmi.transport.tcp.responseTimeout", "3000");
        System.setProperty("sun.rmi.transport.tcp.connectionTimeout", "3000");

        nameField.setPromptText("Ví dụ: Server máy 1");
        nameField.setPrefWidth(180);
        localAddress.setPrefWidth(260);
        peerAddress.setPrefWidth(260);
        VBox nameBox = field("Tên server", nameField);
        VBox localField = field("Địa chỉ máy này (IP:Port)", localAddress);
        VBox peerField = field("Địa chỉ máy kia (IP:Port)", peerAddress);
        HBox addressRow = new HBox(14, nameBox, localField, peerField);
        HBox.setHgrow(localField, Priority.ALWAYS);
        HBox.setHgrow(peerField, Priority.ALWAYS);

        startButton.getStyleClass().add("primary-button");
        stopButton.getStyleClass().add("danger-button");
        stopButton.setDisable(true);
        startButton.setOnAction(event -> startServer());
        stopButton.setOnAction(event -> stopServer());
        HBox buttons = new HBox(10, startButton, stopButton);
        VBox configCard = new VBox(12, sectionTitle("Cấu hình server"), addressRow, buttons, status);
        configCard.getStyleClass().add("card");
        status.getStyleClass().add("status-text");

        Label title = new Label("Theo dõi server RMI");
        title.getStyleClass().add("app-title");
        Label subtitle = new Label("Cấu hình IP:Port và xem hoạt động đồng bộ theo thời gian thực");
        subtitle.getStyleClass().add("subtitle");
        VBox top = new VBox(14, new VBox(2, title, subtitle), configCard);

        HBox firstRow = new HBox(14,
                infoCard("SERVER NÀY", localState, localDetail, localRmiPort),
                infoCard("KẾT NỐI MÁY KIA", peerState, peerName, peerDetail, lastSync, peerError));
        HBox secondRow = new HBox(14,
                infoCard("DỮ LIỆU", noteCount, dataFileLabel),
                infoCard("HOẠT ĐỘNG PHIÊN NÀY", clientChanges, receivedChanges, syncCount));
        for (HBox row : List.of(firstRow, secondRow)) {
            for (var child : row.getChildren()) HBox.setHgrow(child, Priority.ALWAYS);
        }

        noteList.setPlaceholder(new Label("Chưa có ghi chú."));
        noteList.getStyleClass().add("note-list");
        noteList.setCellFactory(view -> new ListCell<>() {
            @Override
            protected void updateItem(Note note, boolean empty) {
                super.updateItem(note, empty);
                if (empty || note == null) {
                    setText(null);
                } else {
                    String preview = note.getText().replace('\n', ' ');
                    if (preview.length() > 40) preview = preview.substring(0, 40) + "…";
                    setText(preview + "\nNguồn: " + note.getServerId() + " · bản " + note.getVersion());
                }
            }
        });
        VBox listCard = new VBox(12, sectionTitle("Ghi chú hiện có"), noteList);
        listCard.getStyleClass().add("card");
        listCard.setPrefWidth(340);
        listCard.setMinWidth(300);
        VBox.setVgrow(noteList, Priority.ALWAYS);

        logArea.setEditable(false);
        logArea.setWrapText(true);
        logArea.setPromptText("Sự kiện của server sẽ xuất hiện ở đây.");
        logArea.getStyleClass().add("server-log");
        VBox logCard = new VBox(12, sectionTitle("Nhật ký hoạt động"), logArea);
        logCard.getStyleClass().add("card");
        HBox.setHgrow(logCard, Priority.ALWAYS);
        VBox.setVgrow(logArea, Priority.ALWAYS);
        HBox details = new HBox(14, listCard, logCard);
        VBox.setVgrow(details, Priority.ALWAYS);

        VBox center = new VBox(14, firstRow, secondRow, details);
        BorderPane root = new BorderPane();
        root.setTop(top);
        root.setCenter(center);
        root.setPadding(new Insets(22));
        BorderPane.setMargin(center, new Insets(16, 0, 0, 0));

        Scene scene = new Scene(root, 900, 760);
        scene.getStylesheets().add(getClass().getResource("/vn/edu/rmi/client/app.css").toExternalForm());
        scene.getStylesheets().add(getClass().getResource("server.css").toExternalForm());
        stage.setTitle("Server RMI - Theo dõi đồng bộ");
        stage.setMinWidth(760);
        stage.setMinHeight(660);
        stage.setScene(scene);
        stage.show();

        monitor = new Timeline(new KeyFrame(Duration.seconds(1), event -> refreshMonitor()));
        monitor.setCycleCount(Timeline.INDEFINITE);
        monitor.play();
    }

    private VBox field(String title, TextField input) {
        Label label = new Label(title);
        label.getStyleClass().add("field-label");
        input.setMaxWidth(Double.MAX_VALUE);
        return new VBox(5, label, input);
    }

    private Label sectionTitle(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("section-title");
        return label;
    }

    private VBox infoCard(String title, Label main, Label... details) {
        Label heading = new Label(title);
        heading.getStyleClass().add("info-heading");
        main.getStyleClass().add("info-value");
        VBox card = new VBox(6, heading, main);
        for (Label detail : details) {
            detail.getStyleClass().add("muted-text");
            detail.setWrapText(true);
            card.getChildren().add(detail);
        }
        card.getStyleClass().add("card");
        card.setMaxWidth(Double.MAX_VALUE);
        return card;
    }

    private void startServer() {
        if (server != null) return;
        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            setStatus("Hãy nhập tên server để dễ nhận diện trên hai máy.", true);
            return;
        }
        Endpoint local;
        Endpoint peer;
        try {
            local = parseEndpoint(localAddress.getText());
            peer = parseEndpoint(peerAddress.getText());
        } catch (IllegalArgumentException e) {
            setStatus("Địa chỉ phải có dạng IP:Port, ví dụ 192.168.1.10:1099 (cổng 1–65534).", true);
            return;
        }
        if (local.equals(peer)) {
            setStatus("Địa chỉ máy này và máy kia phải khác nhau.", true);
            return;
        }

        System.setProperty("java.rmi.server.hostname", local.host());
        String serverId = local.address();
        Path dataFile = Path.of("data", "server-" + local.port() + ".ser");
        NoteServer created = null;
        Registry createdRegistry = null;
        try {
            created = new NoteServer(serverId, name, local.port() + 1, dataFile, peer.host(), peer.port());
            created.setLogListener(this::appendLog);
            createdRegistry = LocateRegistry.createRegistry(local.port());
            createdRegistry.rebind(NoteService.SERVICE_NAME, created);
            NoteServer activeServer = created;
            scheduler = Executors.newSingleThreadScheduledExecutor();
            scheduler.scheduleWithFixedDelay(activeServer::syncWithPeer, 0, 3, TimeUnit.SECONDS);

            server = created;
            registry = createdRegistry;
            setInputsDisabled(true);
            localState.setText(name);
            localDetail.setText("Địa chỉ: " + local.address());
            localRmiPort.setText("Cổng RMI: " + (local.port() + 1));
            peerState.setText("Đang chờ kết nối");
            peerDetail.setText("Địa chỉ: " + peer.address());
            dataFileLabel.setText("File: " + dataFile);
            setStatus("Đang chạy. Client kết nối tới " + local.address(), false);
            stage.setTitle(name + " - Server RMI");
            appendLog("Bắt đầu " + name + " tại " + local.address());
            appendLog("Peer: " + peer.address() + " | Dữ liệu: " + dataFile.toAbsolutePath());
            refreshMonitor();
        } catch (Exception e) {
            if (scheduler != null) scheduler.shutdownNow();
            scheduler = null;
            unexport(createdRegistry);
            unexport(created);
            setStatus("Không chạy được server: " + e.getMessage(), true);
            appendLog("Lỗi khởi động: " + e.getMessage());
        }
    }

    private Endpoint parseEndpoint(String value) {
        String[] parts = value.trim().split(":", -1);
        if (parts.length != 2 || parts[0].isBlank()) throw new IllegalArgumentException();
        try {
            int port = Integer.parseInt(parts[1].trim());
            if (port < 1 || port > 65534) throw new IllegalArgumentException();
            return new Endpoint(parts[0].trim(), port);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private void stopServer() {
        if (server == null) return;
        if (scheduler != null) scheduler.shutdownNow();
        scheduler = null;
        try {
            registry.unbind(NoteService.SERVICE_NAME);
        } catch (Exception e) {
            appendLog("Không gỡ được tên RMI: " + e.getMessage());
        }
        unexport(registry);
        unexport(server);
        registry = null;
        server = null;
        notesOnScreen.clear();
        localState.setText("Chưa chạy");
        localDetail.setText("Địa chỉ: --");
        localRmiPort.setText("Cổng RMI: --");
        peerState.setText("Chưa kết nối");
        peerState.getStyleClass().removeAll("state-online", "state-offline");
        peerName.setText("Tên: chưa biết");
        peerDetail.setText("Địa chỉ: --");
        peerError.setText("");
        lastSync.setText("Lần đồng bộ: chưa có");
        noteCount.setText("0 ghi chú");
        dataFileLabel.setText("File dữ liệu: --");
        clientChanges.setText("0 thao tác client");
        receivedChanges.setText("0 bản nhận từ peer");
        syncCount.setText("0 lần đồng bộ");
        stage.setTitle("Server RMI - Theo dõi đồng bộ");
        setInputsDisabled(false);
        setStatus("Đã dừng. Có thể sửa địa chỉ rồi chạy lại.", false);
        appendLog("Đã dừng server.");
    }

    private void unexport(Remote remote) {
        if (remote == null) return;
        try {
            UnicastRemoteObject.unexportObject(remote, true);
        } catch (Exception ignored) {
            // Đối tượng có thể chưa được export nếu khởi động lỗi.
        }
    }

    private void setInputsDisabled(boolean running) {
        nameField.setDisable(running);
        localAddress.setDisable(running);
        peerAddress.setDisable(running);
        startButton.setDisable(running);
        stopButton.setDisable(!running);
    }

    private void refreshMonitor() {
        NoteServer active = server;
        if (active == null) return;
        List<Note> currentNotes = active.listNotes();
        if (!sameNotes(currentNotes)) notesOnScreen.setAll(currentNotes);
        long deleted = active.getAllNotes().size() - currentNotes.size();
        noteCount.setText(currentNotes.size() + " ghi chú · " + deleted + " đã xóa");
        peerState.setText(active.isPeerOnline() ? "Đã kết nối" : "Mất kết nối / đang chờ");
        peerState.getStyleClass().removeAll("state-online", "state-offline");
        peerState.getStyleClass().add(active.isPeerOnline() ? "state-online" : "state-offline");
        peerName.setText(active.getPeerName().isBlank()
                ? "Tên: chưa biết" : "Tên: " + active.getPeerName());
        peerError.setText(active.isPeerOnline() || active.getLastPeerError().isBlank()
                ? "" : "Lỗi: " + active.getLastPeerError());
        clientChanges.setText(active.getClientChanges() + " thao tác client");
        receivedChanges.setText(active.getReceivedChanges() + " bản nhận từ peer");
        syncCount.setText(active.getSuccessfulSyncs() + " lần đồng bộ");
        long syncTime = active.getLastSyncTime();
        if (syncTime > 0) {
            String time = Instant.ofEpochMilli(syncTime).atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            lastSync.setText("Lần đồng bộ: " + time);
        }
    }

    private boolean sameNotes(List<Note> currentNotes) {
        if (currentNotes.size() != notesOnScreen.size()) return false;
        for (int i = 0; i < currentNotes.size(); i++) {
            Note a = currentNotes.get(i);
            Note b = notesOnScreen.get(i);
            if (!a.getId().equals(b.getId()) || a.getVersion() != b.getVersion()
                    || !a.getServerId().equals(b.getServerId())) return false;
        }
        return true;
    }

    private void setStatus(String message, boolean error) {
        status.setText(message);
        status.getStyleClass().remove("status-error");
        if (error) status.getStyleClass().add("status-error");
    }

    private void appendLog(String message) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> appendLog(message));
            return;
        }
        String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        if (logArea.getLength() > 12000) logArea.deleteText(0, 4000);
        logArea.appendText("[" + time + "] " + message + "\n");
    }

    @Override
    public void stop() {
        if (monitor != null) monitor.stop();
        stopServer();
    }

    private record Endpoint(String host, int port) {
        String address() { return host + ":" + port; }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
