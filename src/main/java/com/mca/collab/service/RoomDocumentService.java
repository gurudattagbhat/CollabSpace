package com.mca.collab.service;

import com.mca.collab.model.RoomDocument;
import com.mca.collab.repository.RoomDocumentRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class RoomDocumentService {

    private final RoomDocumentRepository roomDocumentRepository;

    public RoomDocumentService(RoomDocumentRepository roomDocumentRepository) {
        this.roomDocumentRepository = roomDocumentRepository;
    }

    public String getRoomText(String roomId) {
        Optional<RoomDocument> doc = roomDocumentRepository.findByRoomId(roomId);
        return doc.map(RoomDocument::getTextContent).orElse("");
    }

    @Transactional
    public void updateRoomText(String roomId, String text) {
        String safeText = text == null ? "" : text;
        int updated = roomDocumentRepository.updateTextByRoomId(roomId, safeText);
        if (updated > 0) {
            return;
        }

        RoomDocument doc = new RoomDocument(roomId);
        doc.setTextContent(safeText);
        try {
            roomDocumentRepository.save(doc);
        } catch (DataIntegrityViolationException ex) {
            // Another concurrent request inserted this room first; retry partial update.
            roomDocumentRepository.updateTextByRoomId(roomId, safeText);
        }
    }

    public String getRoomDrawing(String roomId) {
        Optional<RoomDocument> doc = roomDocumentRepository.findByRoomId(roomId);
        return doc.map(RoomDocument::getDrawingData).orElse("");
    }

    @Transactional
    public void updateRoomDrawing(String roomId, String drawingData) {
        String safeDrawingData = drawingData == null ? "" : drawingData;
        int updated = roomDocumentRepository.updateDrawingByRoomId(roomId, safeDrawingData);
        if (updated > 0) {
            return;
        }

        RoomDocument doc = new RoomDocument(roomId);
        doc.setDrawingData(safeDrawingData);
        try {
            roomDocumentRepository.save(doc);
        } catch (DataIntegrityViolationException ex) {
            // Another concurrent request inserted this room first; retry partial update.
            roomDocumentRepository.updateDrawingByRoomId(roomId, safeDrawingData);
        }
    }
}
