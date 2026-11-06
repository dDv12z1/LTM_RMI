package vn.edu.rmi.client;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
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
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import vn.edu.rmi.shared.Note;
import vn.edu.rmi.shared.NoteService;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ClientApp extends Application {
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor();
    private final ObservableList<Note> items = FXCollections.observableArrayList();
    private volatile NoteService service;

    private final TextField hostField = new TextField("127.0.0.1");
    private final TextField portField = new TextField("1099");
    private final Label status = new Label("Nhập địa chỉ server để bắt đầu.");
    private final Label connectionState = new Label("Chưa kết nối");
    private final Label connectedServerName = new Label("Server đang dùng: chưa chọn");
    private final Label noteCount = new Label("0 ghi chú");
    private final Label editorTitle = new Label("Tạo ghi chú");
    private final ListView<Note> list = new ListView<>(items);
    private final TextArea editor = new TextArea();
    private final Button saveButton = new Button("Lưu thay đổi");
    private final Button deleteButton = new Button("Xóa");
    private boolean refreshing = false;
    private Stage stage;

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        System.setProperty("sun.rmi.transport.tcp.responseTimeout", "3000");
        System.setProperty("sun.rmi.transport.tcp.connectionTimeout", "3000");

        hostField.setPromptText("VD: 192.168.1.10");
        hostField.setPrefWidth(190);
        portField.setPrefWidth(80);
        Button connectButton = new Button("Kết nối");
        connectButton.getStyleClass().add("primary-button");
        connectButton.setOnAction(event -> connect());
        Region connectionSpace = new Region();
        HBox.setHgrow(connectionSpace, Priority.ALWAYS);
        connectionState.getStyleClass().add("connection-state");
        HBox connectionRow = new HBox(10, new Label("Server"), hostField,
                new Label("Cổng"), portField, connectButton, connectionSpace, connectionState);
        connectionRow.setAlignment(Pos.CENTER_LEFT);
        connectedServerName.getStyleClass().add("muted-text");
        VBox connection = new VBox(8, connectionRow, connectedServerName);
        connection.getStyleClass().add("card");

        Label appTitle = new Label("Ghi chú chung");
        appTitle.getStyleClass().add("app-title");
        Label subtitle = new Label("Đồng bộ dữ liệu giữa hai server bằng Java RMI");
        subtitle.getStyleClass().add("subtitle");
        VBox top = new VBox(12, new VBox(2, appTitle, subtitle), connection);

        list.setCellFactory(view -> new ListCell<>() {
            private final Label preview = new Label();
            private final Label detail = new Label();
            private final VBox content = new VBox(4, preview, detail);
            {
                preview.getStyleClass().add("note-preview");
                detail.getStyleClass().add("note-detail");
            }

            @Override
            protected void updateItem(Note note, boolean empty) {
                super.updateItem(note, empty);
                if (empty || note == null) {
                    setGraphic(null);
                } else {
                    String oneLine = note.getText().replace('\n', ' ');
                    preview.setText(oneLine.length() > 55 ? oneLine.substring(0, 55) + "…" : oneLine);
                    detail.setText("Cập nhật từ server " + note.getServerId());
                    setGraphic(content);
                }
            }
        });
        list.setPlaceholder(new Label("Chưa có ghi chú nào.\nHãy thêm ghi chú đầu tiên."));
        list.getStyleClass().add("note-list");

        editor.setPromptText("Nhập nội dung ghi chú...");
        editor.setWrapText(true);
        editor.getStyleClass().add("note-editor");
        Button addButton = new Button("Thêm ghi chú");
        addButton.getStyleClass().add("primary-button");
        deleteButton.getStyleClass().add("danger-button");
        saveButton.setDisable(true);
        deleteButton.setDisable(true);
        addButton.setOnAction(event -> addNote());
        saveButton.setOnAction(event -> editNote());
        deleteButton.setOnAction(event -> deleteNote());
        list.getSelectionModel().selectedItemProperty().addListener((obs, oldNote, newNote) -> {
            if (!refreshing) {
                editor.setText(newNote == null ? "" : newNote.getText());
                updateSelection();
            }
        });

        Button newButton = new Button("Soạn mới");
        newButton.setOnAction(event -> {
            list.getSelectionModel().clearSelection();
            editor.clear();
            editor.requestFocus();
        });
        Label listTitle = new Label("Danh sách ghi chú");
        listTitle.getStyleClass().add("section-title");
        noteCount.getStyleClass().add("muted-text");
        VBox listHeading = new VBox(2, listTitle, noteCount);
        Region listSpace = new Region();
        HBox.setHgrow(listSpace, Priority.ALWAYS);
        HBox listToolbar = new HBox(8, listHeading, listSpace, newButton);
        listToolbar.setAlignment(Pos.CENTER_LEFT);
        VBox left = new VBox(14, listToolbar, list);
        left.getStyleClass().add("card");
        left.setPrefWidth(300);
        left.setMinWidth(300);
        VBox.setVgrow(list, Priority.ALWAYS);

        editorTitle.getStyleClass().add("section-title");
        Label editorHint = new Label("Chọn ghi chú để sửa, hoặc nhập nội dung mới.");
        editorHint.getStyleClass().add("muted-text");
        editorHint.setWrapText(true);
        HBox actions = new HBox(10, addButton, saveButton, deleteButton);
        VBox right = new VBox(12, editorTitle, editorHint, editor, actions);
        right.getStyleClass().add("card");
        right.setMinWidth(0);
        HBox.setHgrow(right, Priority.ALWAYS);
        VBox.setVgrow(editor, Priority.ALWAYS);

        HBox content = new HBox(16, left, right);
        BorderPane root = new BorderPane();
        root.setTop(top);
        root.setCenter(content);
        root.setBottom(status);
        root.setPadding(new Insets(22));
        BorderPane.setMargin(content, new Insets(18, 0, 0, 0));
        BorderPane.setMargin(status, new Insets(14, 2, 0, 2));
        status.getStyleClass().add("status-text");

        stage.setTitle("Ghi chú chung - Java RMI");
        Scene scene = new Scene(root, 850, 540);
        scene.getStylesheets().add(getClass().getResource("app.css").toExternalForm());
        stage.setMinWidth(720);
        stage.setMinHeight(470);
        stage.setScene(scene);
        stage.show();

        worker.scheduleWithFixedDelay(this::loadNotes, 2, 2, TimeUnit.SECONDS);
        String configuredHost = System.getenv("LAB5_RMI_SERVER_HOST");
        String configuredPort = System.getenv("LAB5_RMI_SERVER_PORT");
        if (configuredHost != null && !configuredHost.isBlank()) {
            hostField.setText(configuredHost);
            if (configuredPort != null && !configuredPort.isBlank()) portField.setText(configuredPort);
            connect();
        }
    }

    private void connect() {
        String host = hostField.getText().trim();
        int port;
        try {
            port = Integer.parseInt(portField.getText().trim());
            if (port < 1 || port > 65535) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            setStatus("Cổng phải là số từ 1 đến 65535.", true);
            return;
        }
        setStatus("Đang kết nối...", false);
        worker.execute(() -> {
            try {
                Registry registry = LocateRegistry.getRegistry(host, port);
                NoteService found = (NoteService) registry.lookup(NoteService.SERVICE_NAME);
                List<Note> notes = found.listNotes();
                String serverName = found.getServerName();
                service = found;
                Platform.runLater(() -> {
                    if (service != found) return;
                    showNotes(notes);
                    setConnectionState(true);
                    connectedServerName.setText("Server đang dùng: " + serverName + " (" + host + ":" + port + ")");
                    stage.setTitle("Ghi chú chung - " + serverName);
                    setStatus("Đã kết nối " + host + ":" + port, false);
                });
            } catch (Exception e) {
                service = null;
                Platform.runLater(() -> {
                    items.clear();
                    noteCount.setText("0 ghi chú");
                    editor.clear();
                    setConnectionState(false);
                    connectedServerName.setText("Server đang dùng: chưa chọn");
                    stage.setTitle("Ghi chú chung - Java RMI");
                    setStatus("Không kết nối được: " + e.getMessage(), true);
                });
            }
        });
    }

    private void loadNotes() {
        NoteService current = service;
        if (current == null) return;
        try {
            List<Note> notes = current.listNotes();
            Platform.runLater(() -> {
                if (service == current) {
                    showNotes(notes);
                    if (connectionState.getText().equals("Mất kết nối")) {
                        setConnectionState(true);
                        setStatus("Đã kết nối lại với server.", false);
                    }
                }
            });
        } catch (Exception e) {
            Platform.runLater(() -> {
                if (service == current) {
                    setConnectionState(false);
                    setStatus("Mất kết nối: " + e.getMessage(), true);
                }
            });
        }
    }

    private void showNotes(List<Note> notes) {
        noteCount.setText(notes.size() + " ghi chú");
        if (sameNotes(notes)) return;
        Note selected = list.getSelectionModel().getSelectedItem();
        String selectedId = selected == null ? null : selected.getId();
        refreshing = true;
        items.setAll(notes);
        boolean found = false;
        if (selectedId != null) {
            for (Note note : notes) {
                if (note.getId().equals(selectedId)) {
                    list.getSelectionModel().select(note);
                    if (!editor.isFocused()) editor.setText(note.getText());
                    found = true;
                    break;
                }
            }
        }
        if (selectedId != null && !found) editor.clear();
        refreshing = false;
        updateSelection();
    }

    private boolean sameNotes(List<Note> notes) {
        if (notes.size() != items.size()) return false;
        for (int i = 0; i < notes.size(); i++) {
            Note a = notes.get(i);
            Note b = items.get(i);
            if (!a.getId().equals(b.getId()) || a.getVersion() != b.getVersion()
                    || !a.getServerId().equals(b.getServerId())) return false;
        }
        return true;
    }

    private void updateSelection() {
        boolean selected = list.getSelectionModel().getSelectedItem() != null;
        editorTitle.setText(selected ? "Chỉnh sửa ghi chú" : "Tạo ghi chú");
        saveButton.setDisable(!selected);
        deleteButton.setDisable(!selected);
    }

    private void setConnectionState(boolean connected) {
        connectionState.setText(connected ? "Đã kết nối" : "Mất kết nối");
        connectionState.getStyleClass().remove("connection-online");
        if (connected) connectionState.getStyleClass().add("connection-online");
    }

    private void setStatus(String message, boolean error) {
        status.setText(message);
        status.getStyleClass().remove("status-error");
        if (error) status.getStyleClass().add("status-error");
    }

    private void addNote() {
        String text = editor.getText().trim();
        if (text.isEmpty()) { setStatus("Hãy nhập nội dung.", true); return; }
        runAction(server -> server.addNote(text), "Đã thêm ghi chú.", true);
    }

    private void editNote() {
        Note selected = list.getSelectionModel().getSelectedItem();
        if (selected == null) { setStatus("Hãy chọn ghi chú cần sửa.", true); return; }
        String text = editor.getText().trim();
        if (text.isEmpty()) { setStatus("Hãy nhập nội dung.", true); return; }
        runAction(server -> server.editNote(selected.getId(), text), "Đã sửa ghi chú.", false);
    }

    private void deleteNote() {
        Note selected = list.getSelectionModel().getSelectedItem();
        if (selected == null) { setStatus("Hãy chọn ghi chú cần xóa.", true); return; }
        runAction(server -> { server.deleteNote(selected.getId()); return null; }, "Đã xóa ghi chú.", true);
    }

    private void runAction(RemoteAction action, String message, boolean clearEditor) {
        NoteService current = service;
        if (current == null) { setStatus("Hãy kết nối server trước.", true); return; }
        worker.execute(() -> {
            try {
                action.run(current);
                List<Note> notes = current.listNotes();
                Platform.runLater(() -> {
                    if (service != current) return;
                    if (clearEditor) {
                        list.getSelectionModel().clearSelection();
                        editor.clear();
                    }
                    showNotes(notes);
                    setStatus(message, false);
                });
            } catch (Exception e) {
                Platform.runLater(() -> setStatus("Lỗi: " + e.getMessage(), true));
            }
        });
    }

    @Override
    public void stop() {
        worker.shutdownNow();
    }

    @FunctionalInterface
    private interface RemoteAction {
        Note run(NoteService server) throws Exception;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
