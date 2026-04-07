package com.mca.collab.controller;

import com.mca.collab.service.RoomDocumentService;
import com.mca.collab.service.UserRoomService;
import com.mca.collab.dto.DocumentUpdateRequest;
import com.mca.collab.dto.RecentRoomResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Controller
public class WorkspaceController {

    private final RoomDocumentService roomDocumentService;
    private final UserRoomService userRoomService;

    public WorkspaceController(RoomDocumentService roomDocumentService,
                               UserRoomService userRoomService) {
        this.roomDocumentService = roomDocumentService;
        this.userRoomService = userRoomService;
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model, Principal principal) {
        boolean isAuthenticated = principal != null;
        model.addAttribute("isAuthenticated", isAuthenticated);
        model.addAttribute("displayName", isAuthenticated ? principal.getName() : null);
        model.addAttribute("userEmail", isAuthenticated ? principal.getName() : null);
        // Provide empty signupRequest for the embedded modal form
        if (!model.containsAttribute("signupRequest")) {
            model.addAttribute("signupRequest", new com.mca.collab.dto.SignupRequest());
        }
        return "dashboard";
    }

    @GetMapping("/workspace")
    public String createOrJoinWorkspace(@RequestParam(required = false) String roomId) {
        String targetRoom = (roomId == null || roomId.isBlank()) ? UUID.randomUUID().toString().substring(0, 8) : roomId;
        return "redirect:/workspace/" + targetRoom;
    }

    @GetMapping("/workspace/{roomId}")
    public String workspace(@PathVariable String roomId, Model model, Principal principal) {
        if (principal != null) {
            userRoomService.touchRoomForUser(principal.getName(), roomId);
        }

        model.addAttribute("roomId", roomId);
        String username = (principal != null)
                ? principal.getName()
                : "Guest-" + roomId.substring(0, Math.min(4, roomId.length()));
        model.addAttribute("username", username);
        model.addAttribute("isAuthenticated", principal != null);
        return "workspace";
    }

    @GetMapping("/api/rooms/{roomId}/document")
    public ResponseEntity<Map<String, String>> getDocument(@PathVariable String roomId) {
        Map<String, String> response = new HashMap<>();
        response.put("content", roomDocumentService.getRoomText(roomId));
        response.put("drawingData", roomDocumentService.getRoomDrawing(roomId));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/api/rooms/{roomId}/document")
    public ResponseEntity<Void> updateDocument(@PathVariable String roomId, @RequestBody DocumentUpdateRequest request) {
        try {
            String content = (request != null && request.getContent() != null) ? request.getContent() : "";
            System.out.println("[SAVE] Document: room=" + roomId + ", size=" + content.length());
            roomDocumentService.updateRoomText(roomId, content);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            System.err.println("[ERROR] Document save failed for " + roomId + ": " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).build();
        }
    }

    @PostMapping("/api/rooms/{roomId}/drawing")
    public ResponseEntity<Void> updateDrawing(@PathVariable String roomId, @RequestBody DocumentUpdateRequest request) {
        try {
            String drawingData = (request != null && request.getDrawingData() != null) ? request.getDrawingData() : "";
            System.out.println("[SAVE] Drawing: room=" + roomId + ", size=" + (drawingData.length()/1024) + "KB");
            roomDocumentService.updateRoomDrawing(roomId, drawingData);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            System.err.println("[ERROR] Drawing save failed for " + roomId + ": " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).build();
        }
    }

    @PostMapping("/api/users/me/rooms/sync")
    public ResponseEntity<Void> syncMyRooms(@RequestBody Map<String, java.util.List<String>> payload,
                                            Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        userRoomService.syncRoomsForUser(principal.getName(), payload.get("roomIds"));
        return ResponseEntity.ok().build();
    }

    @GetMapping("/api/users/me/rooms")
    public ResponseEntity<java.util.List<RecentRoomResponse>> getMyRooms(Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(userRoomService.getRecentRoomResponsesForUser(principal.getName()));
    }
}
