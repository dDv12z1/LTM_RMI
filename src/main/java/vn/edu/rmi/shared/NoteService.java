package vn.edu.rmi.shared;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

/** Các hàm mà client và server bên kia có thể gọi từ xa. */
public interface NoteService extends Remote {
    String SERVICE_NAME = "NoteService";

    String getServerName() throws RemoteException;
    List<Note> listNotes() throws RemoteException;
    Note addNote(String text) throws RemoteException;
    Note editNote(String id, String text) throws RemoteException;
    void deleteNote(String id) throws RemoteException;

    // Hai hàm dưới chỉ phục vụ đồng bộ giữa hai server.
    List<Note> getAllNotes() throws RemoteException;
    void receiveNote(Note note) throws RemoteException;
}
