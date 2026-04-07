package com.mca.collab.dto;

public class DocumentUpdateRequest {
    private String content;
    private String drawingData;

    public DocumentUpdateRequest() {
    }

    public DocumentUpdateRequest(String content, String drawingData) {
        this.content = content;
        this.drawingData = drawingData;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getDrawingData() {
        return drawingData;
    }

    public void setDrawingData(String drawingData) {
        this.drawingData = drawingData;
    }
}
