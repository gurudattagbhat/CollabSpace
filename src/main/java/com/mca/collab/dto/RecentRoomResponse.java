package com.mca.collab.dto;

public class RecentRoomResponse {

    private String id;
    private long time;

    public RecentRoomResponse() {
    }

    public RecentRoomResponse(String id, long time) {
        this.id = id;
        this.time = time;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
    }
}
