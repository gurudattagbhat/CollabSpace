package com.mca.collab.service;

import com.mca.collab.dto.ForgotPasswordRequest;
import com.mca.collab.dto.OtpVerificationRequest;
import com.mca.collab.dto.ResetPasswordRequest;
import com.mca.collab.dto.SignupRequest;
import com.mca.collab.model.AppUser;
import com.mca.collab.model.OtpToken;
import com.mca.collab.model.PasswordResetToken;
import com.mca.collab.repository.OtpTokenRepository;
import com.mca.collab.repository.PasswordResetTokenRepository;
import com.mca.collab.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Random;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final OtpTokenRepository otpTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final int otpExpiryMinutes;
    private final int resetExpiryMinutes;
    private final boolean requireMailSuccess;

    public AuthService(UserRepository userRepository,
                       OtpTokenRepository otpTokenRepository,
                       PasswordResetTokenRepository passwordResetTokenRepository,
                       PasswordEncoder passwordEncoder,
                       EmailService emailService,
                       @Value("${app.security.otp-expiry-minutes}") int otpExpiryMinutes,
                       @Value("${app.security.reset-expiry-minutes}") int resetExpiryMinutes,
                       @Value("${app.mail.require-success:false}") boolean requireMailSuccess) {
        this.userRepository = userRepository;
        this.otpTokenRepository = otpTokenRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.otpExpiryMinutes = otpExpiryMinutes;
        this.resetExpiryMinutes = resetExpiryMinutes;
        this.requireMailSuccess = requireMailSuccess;
    }

    @Transactional
    public void register(SignupRequest request) {
        String normalizedEmail = request.getEmail().toLowerCase().trim();
        AppUser user;

        Optional<AppUser> existingOpt = userRepository.findByEmail(normalizedEmail);
        if (existingOpt.isPresent()) {
            user = existingOpt.get();
            if (user.isEnabled() || user.isVerified()) {
                throw new IllegalArgumentException("Email is already registered. Please log in or use Forgot Password.");
            }
        } else {
            user = new AppUser();
            user.setEmail(normalizedEmail);
        }

        user.setFullName(request.getFullName().trim());
        user.setDisplayName(request.getFullName().trim());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setEnabled(false);
        user.setVerified(false);

        try {
            userRepository.save(user);
        } catch (DataIntegrityViolationException e) {
            throw new IllegalArgumentException("Email is already registered. Please log in or use Forgot Password.");
        }

        otpTokenRepository.findAllByUser(user).forEach(token -> {
            if (!token.isUsed()) {
                token.setUsed(true);
            }
        });

        sendOtp(user);
    }

    @Transactional
    public void resendOtp(String email) {
        AppUser user = userRepository.findByEmail(email.toLowerCase().trim())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (user.isEnabled()) {
            throw new IllegalStateException("User already verified");
        }
        sendOtp(user);
    }

    @Transactional
    public void verifyOtp(OtpVerificationRequest request) {
        AppUser user = userRepository.findByEmail(request.getEmail().toLowerCase().trim())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        OtpToken token = otpTokenRepository.findTopByUserOrderByIdDesc(user)
                .orElseThrow(() -> new IllegalArgumentException("OTP not found"));

        if (token.isUsed()) {
            throw new IllegalArgumentException("OTP already used");
        }
        if (token.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("OTP expired");
        }
        if (!token.getOtp().equals(request.getOtp().trim())) {
            throw new IllegalArgumentException("Invalid OTP");
        }

        token.setUsed(true);
        otpTokenRepository.save(token);

        user.setEnabled(true);
        user.setVerified(true);
        userRepository.save(user);
    }

    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        String normalizedEmail = request.getEmail() == null ? "" : request.getEmail().toLowerCase().trim();
        if (normalizedEmail.isBlank()) {
            throw new IllegalArgumentException("Email is required");
        }

        Optional<AppUser> userOpt = userRepository.findByEmail(normalizedEmail);
        if (userOpt.isEmpty()) {
            throw new IllegalArgumentException("No account found with this email.");
        }

        AppUser user = userOpt.get();

        String token = String.format("%06d", new Random().nextInt(1_000_000));
        passwordResetTokenRepository.findAllByUserAndUsedFalse(user).forEach(t -> t.setUsed(true));

        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setUser(user);
        resetToken.setToken(token);
        resetToken.setExpiresAt(LocalDateTime.now().plusMinutes(resetExpiryMinutes));
        resetToken.setUsed(false);
        passwordResetTokenRepository.save(resetToken);

        boolean sent = emailService.send(
                user.getEmail(),
                "Reset OTP for your CollabSpace account",
                "Your password reset OTP is: " + token + "\nIt expires in " + resetExpiryMinutes + " minutes."
        );
        if (!sent && requireMailSuccess) {
            throw new IllegalStateException("Unable to send reset OTP email. Please verify mail settings and try again.");
        }
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        AppUser user = userRepository.findByEmail(request.getEmail().toLowerCase().trim())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        String resetToken = request.getToken();
        if (resetToken == null || resetToken.isBlank()) {
            resetToken = request.getOtp();
        }
        if (resetToken == null || resetToken.isBlank()) {
            throw new IllegalArgumentException("Reset OTP is required");
        }

        PasswordResetToken token = passwordResetTokenRepository
                .findTopByUserAndTokenAndUsedFalse(user, resetToken.trim())
                .orElseThrow(() -> new IllegalArgumentException("Invalid reset OTP"));

        if (token.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Reset OTP expired");
        }

        String newPassword = request.getNewPassword();
        if (newPassword == null || newPassword.isBlank()) {
            newPassword = request.getPassword();
        }
        if (newPassword == null || newPassword.length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        // Reset OTP proves mailbox ownership, so allow login even if signup verification
        // was not completed previously.
        user.setEnabled(true);
        user.setVerified(true);
        userRepository.save(user);

        token.setUsed(true);
        passwordResetTokenRepository.save(token);
    }

    private void sendOtp(AppUser user) {
        String otp = String.format("%06d", new Random().nextInt(1_000_000));

        OtpToken token = new OtpToken();
        token.setUser(user);
        token.setOtp(otp);
        token.setExpiresAt(LocalDateTime.now().plusMinutes(otpExpiryMinutes));
        token.setUsed(false);
        otpTokenRepository.save(token);

        boolean sent = emailService.send(
                user.getEmail(),
                "Verify your Collaborative Workspace account",
                "Your OTP is: " + otp + "\nIt expires in " + otpExpiryMinutes + " minutes."
        );
        if (!sent && requireMailSuccess) {
            throw new IllegalStateException("Unable to send verification OTP email. Please verify mail settings and try again.");
        }
    }
}
