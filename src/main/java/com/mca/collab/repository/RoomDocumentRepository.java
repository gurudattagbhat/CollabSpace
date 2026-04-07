package com.mca.collab.repository;

import com.mca.collab.model.RoomDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RoomDocumentRepository extends JpaRepository<RoomDocument, Long> {
    Optional<RoomDocument> findByRoomId(String roomId);

    @Modifying
    @Query("update RoomDocument r set r.textContent = :text where r.roomId = :roomId")
    int updateTextByRoomId(@Param("roomId") String roomId, @Param("text") String text);

    @Modifying
    @Query("update RoomDocument r set r.drawingData = :drawingData where r.roomId = :roomId")
    int updateDrawingByRoomId(@Param("roomId") String roomId, @Param("drawingData") String drawingData);
}
