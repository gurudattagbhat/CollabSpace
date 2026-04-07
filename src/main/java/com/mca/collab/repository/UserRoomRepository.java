package com.mca.collab.repository;

import com.mca.collab.model.UserRoom;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRoomRepository extends JpaRepository<UserRoom, Long> {
    Optional<UserRoom> findByUserIdAndRoomId(Long userId, String roomId);
    List<UserRoom> findTop20ByUserIdOrderByLastVisitedAtDesc(Long userId);
}