package com.mca.collab.controller;

import com.mca.collab.dto.TextSyncMessage;
import com.mca.collab.dto.WhiteboardStrokeMessage;
import com.mca.collab.service.RoomDocumentService;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
public class CollaborationSocketController {

    private final SimpMessagingTemplate messagingTemplate;
    private final RoomDocumentService roomDocumentService;

    public CollaborationSocketController(SimpMessagingTemplate messagingTemplate,
                                         RoomDocumentService roomDocumentService) {
        this.messagingTemplate = messagingTemplate;
        this.roomDocumentService = roomDocumentService;
    }

    @MessageMapping("/room/{roomId}/draw")
    public void broadcastDraw(@DestinationVariable String roomId, WhiteboardStrokeMessage message) {
        messagingTemplate.convertAndSend("/topic/room/" + roomId + "/draw", message);
    }

    @MessageMapping("/room/{roomId}/text")
    public void broadcastText(@DestinationVariable String roomId, TextSyncMessage message) {
        roomDocumentService.updateRoomText(roomId, message.getContent());
        messagingTemplate.convertAndSend("/topic/room/" + roomId + "/text", message);
    }
}
