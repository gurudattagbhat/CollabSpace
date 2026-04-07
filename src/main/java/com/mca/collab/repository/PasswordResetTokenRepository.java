package com.mca.collab.repository;

import com.mca.collab.model.AppUser;
import com.mca.collab.model.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
    Optional<PasswordResetToken> findTopByTokenAndUsedFalse(String token);
    Optional<PasswordResetToken> findTopByUserAndTokenAndUsedFalse(AppUser user, String token);
    List<PasswordResetToken> findAllByUserAndUsedFalse(AppUser user);
}
