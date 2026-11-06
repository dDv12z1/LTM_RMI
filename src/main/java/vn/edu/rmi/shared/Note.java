package vn.edu.rmi.shared;

import java.io.Serializable;

/** Một bản ghi được gửi qua RMI giữa client và hai server. */
public class Note implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String id;
    private final String text;
    private final long version;
    private final String serverId;
    private final boolean deleted;

    public Note(String id, String text, long version, String serverId, boolean deleted) {
        this.id = id;
        this.text = text;
        this.version = version;
        this.serverId = serverId;
        this.deleted = deleted;
    }

    public String getId() { return id; }
    public String getText() { return text; }
    public long getVersion() { return version; }
    public String getServerId() { return serverId; }
    public boolean isDeleted() { return deleted; }
}
