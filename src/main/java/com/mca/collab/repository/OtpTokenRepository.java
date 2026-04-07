package com.mca.collab.repository;

import com.mca.collab.model.AppUser;
import com.mca.collab.model.OtpToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OtpTokenRepository extends JpaRepository<OtpToken, Long> {
    Optional<OtpToken> findTopByUserOrderByIdDesc(AppUser user);
    List<OtpToken> findAllByUser(AppUser user);
}
