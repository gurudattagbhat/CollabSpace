package com.mca.collab.service;

import com.mca.collab.dto.RecentRoomResponse;
import com.mca.collab.model.AppUser;
import com.mca.collab.model.UserRoom;
import com.mca.collab.repository.UserRepository;
import com.mca.collab.repository.UserRoomRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Service
public class UserRoomService {

    private final UserRepository userRepository;
    private final UserRoomRepository userRoomRepository;

    public UserRoomService(UserRepository userRepository, UserRoomRepository userRoomRepository) {
        this.userRepository = userRepository;
        this.userRoomRepository = userRoomRepository;
    }

    @Transactional
    public void touchRoomForUser(String userEmail, String roomId) {
        if (userEmail == null || userEmail.isBlank() || roomId == null || roomId.isBlank()) {
            return;
        }

        Optional<AppUser> userOpt = userRepository.findByEmail(userEmail.toLowerCase().trim());
        if (userOpt.isEmpty()) {
            return;
        }

        AppUser user = userOpt.get();
        UserRoom userRoom = userRoomRepository.findByUserIdAndRoomId(user.getId(), roomId)
                .orElseGet(UserRoom::new);

        userRoom.setUser(user);
        userRoom.setRoomId(roomId);
        userRoom.setLastVisitedAt(LocalDateTime.now());
        userRoomRepository.save(userRoom);
    }

    @Transactional
    public void syncRoomsForUser(String userEmail, Collection<String> roomIds) {
        if (roomIds == null || roomIds.isEmpty()) {
            return;
        }
        if (userEmail == null || userEmail.isBlank()) {
            return;
        }

        Optional<AppUser> userOpt = userRepository.findByEmail(userEmail.toLowerCase().trim());
        if (userOpt.isEmpty()) {
            return;
        }

        AppUser user = userOpt.get();
        for (String roomId : roomIds) {
            if (roomId == null || roomId.isBlank()) {
                continue;
            }

            boolean exists = userRoomRepository.findByUserIdAndRoomId(user.getId(), roomId).isPresent();
            if (!exists) {
                UserRoom userRoom = new UserRoom();
                userRoom.setUser(user);
                userRoom.setRoomId(roomId);
                userRoom.setLastVisitedAt(LocalDateTime.now());
                userRoomRepository.save(userRoom);
            }
        }
    }

    public List<String> getRecentRoomsForUser(String userEmail) {
        if (userEmail == null || userEmail.isBlank()) {
            return List.of();
        }
        Optional<AppUser> userOpt = userRepository.findByEmail(userEmail.toLowerCase().trim());
        if (userOpt.isEmpty()) {
            return List.of();
        }

        return userRoomRepository.findTop20ByUserIdOrderByLastVisitedAtDesc(userOpt.get().getId())
                .stream()
                .map(UserRoom::getRoomId)
                .toList();
    }

    public List<RecentRoomResponse> getRecentRoomResponsesForUser(String userEmail) {
        if (userEmail == null || userEmail.isBlank()) {
            return List.of();
        }
        Optional<AppUser> userOpt = userRepository.findByEmail(userEmail.toLowerCase().trim());
        if (userOpt.isEmpty()) {
            return List.of();
        }

        return userRoomRepository.findTop20ByUserIdOrderByLastVisitedAtDesc(userOpt.get().getId())
                .stream()
                .map(room -> new RecentRoomResponse(
                        room.getRoomId(),
                        room.getLastVisitedAt() != null
                                ? room.getLastVisitedAt().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                                : System.currentTimeMillis()))
                .toList();
    }
}