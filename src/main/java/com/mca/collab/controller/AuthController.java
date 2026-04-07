package com.mca.collab.controller;

import com.mca.collab.dto.ForgotPasswordRequest;
import com.mca.collab.dto.OtpVerificationRequest;
import com.mca.collab.dto.ResetPasswordRequest;
import com.mca.collab.dto.SignupRequest;
import com.mca.collab.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Controller
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/")
    public String home() {
        return "redirect:/dashboard";
    }

    @GetMapping("/login")
    public String login() {
        return "redirect:/dashboard";
    }



    @PostMapping("/signup")
    public String signupSubmit(@Valid @ModelAttribute SignupRequest signupRequest,
                               BindingResult bindingResult,
                               Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("error", "Please fill in all fields correctly.");
            model.addAttribute("signupRequest", signupRequest);
            model.addAttribute("isAuthenticated", false);
            return "dashboard";
        }

        try {
            authService.register(signupRequest);
            String email = URLEncoder.encode(signupRequest.getEmail(), StandardCharsets.UTF_8);
            return "redirect:/verify-otp?email=" + email + "&signup=true";
        } catch (Exception ex) {
            model.addAttribute("error", ex.getMessage());
            model.addAttribute("signupRequest", signupRequest);
            model.addAttribute("isAuthenticated", false);
            return "dashboard";
        }
    }

    @GetMapping("/verify-otp")
    public String verifyOtpPage(@RequestParam(required = false) String email, Model model) {
        if (!model.containsAttribute("otpRequest")) {
            OtpVerificationRequest request = new OtpVerificationRequest();
            request.setEmail(email);
            model.addAttribute("otpRequest", request);
        }
        return "verify-otp";
    }

    @PostMapping("/verify-otp")
    public String verifyOtpSubmit(@Valid @ModelAttribute("otpRequest") OtpVerificationRequest otpRequest,
                                  BindingResult bindingResult,
                                  Model model) {
        if (bindingResult.hasErrors()) {
            return "verify-otp";
        }

        try {
            authService.verifyOtp(otpRequest);
            return "redirect:/dashboard?login=true&verified=true";
        } catch (Exception ex) {
            model.addAttribute("error", ex.getMessage());
            return "verify-otp";
        }
    }

    @PostMapping("/resend-otp")
    public String resendOtp(@RequestParam String email, Model model) {
        try {
            authService.resendOtp(email);
            String encodedEmail = URLEncoder.encode(email, StandardCharsets.UTF_8);
            return "redirect:/verify-otp?email=" + encodedEmail + "&resent=true";
        } catch (Exception ex) {
            model.addAttribute("error", ex.getMessage());
            OtpVerificationRequest request = new OtpVerificationRequest();
            request.setEmail(email);
            model.addAttribute("otpRequest", request);
            return "verify-otp";
        }
    }

    @GetMapping("/forgot-password")
    public String forgotPasswordPage(@RequestParam(required = false) String email) {
        String encodedEmail = email == null ? "" : URLEncoder.encode(email, StandardCharsets.UTF_8);
        return "redirect:/dashboard?forgot=true" + (encodedEmail.isBlank() ? "" : "&email=" + encodedEmail);
    }

    @PostMapping("/forgot-password")
    public String forgotPasswordSubmit(@RequestParam String email, Model model) {
        try {
            authService.forgotPassword(new ForgotPasswordRequest(email));
            String encodedEmail = URLEncoder.encode(email, StandardCharsets.UTF_8);
            return "redirect:/dashboard?forgot-otp=true&forgotSent=true&email=" + encodedEmail;
        } catch (Exception ex) {
            String encodedEmail = email == null ? "" : URLEncoder.encode(email, StandardCharsets.UTF_8);
            return "redirect:/dashboard?forgot=true&forgotError=" + URLEncoder.encode(ex.getMessage(), StandardCharsets.UTF_8)
                    + (encodedEmail.isBlank() ? "" : "&email=" + encodedEmail);
        }
    }

    @PostMapping("/forgot-password/reset")
    public String forgotPasswordResetSubmit(@RequestParam String email,
                                           @RequestParam String otp,
                                           @RequestParam String password) {
        try {
            ResetPasswordRequest resetRequest = new ResetPasswordRequest();
            resetRequest.setEmail(email);
            resetRequest.setOtp(otp);
            resetRequest.setPassword(password);
            authService.resetPassword(resetRequest);
            return "redirect:/dashboard?login=true&success=true&reset=true";
        } catch (Exception ex) {
            String encodedEmail = email == null ? "" : URLEncoder.encode(email, StandardCharsets.UTF_8);
            return "redirect:/dashboard?forgot-otp=true&forgotError=" + URLEncoder.encode(ex.getMessage(), StandardCharsets.UTF_8)
                    + (encodedEmail.isBlank() ? "" : "&email=" + encodedEmail);
        }
    }

    @GetMapping("/reset-password")
    public String resetPasswordPage(@RequestParam(required = false) String email) {
        String encodedEmail = email == null ? "" : URLEncoder.encode(email, StandardCharsets.UTF_8);
        return "redirect:/forgot-password" + (encodedEmail.isBlank() ? "" : "?email=" + encodedEmail);
    }

    @PostMapping("/reset-password")
    public String resetPasswordSubmit(@Valid @ModelAttribute ResetPasswordRequest resetPasswordRequest,
                                      BindingResult bindingResult,
                                      Model model) {
        if (bindingResult.hasErrors()) {
            String email = resetPasswordRequest.getEmail() == null ? "" : URLEncoder.encode(resetPasswordRequest.getEmail(), StandardCharsets.UTF_8);
            return "redirect:/dashboard?forgot=true&forgotError=Invalid%20reset%20request" + (email.isBlank() ? "" : "&email=" + email);
        }

        try {
            authService.resetPassword(resetPasswordRequest);
            return "redirect:/dashboard?login=true&success=true&reset=true";
        } catch (Exception ex) {
            String email = resetPasswordRequest.getEmail() == null ? "" : URLEncoder.encode(resetPasswordRequest.getEmail(), StandardCharsets.UTF_8);
            return "redirect:/dashboard?forgot=true&forgotError=" + URLEncoder.encode(ex.getMessage(), StandardCharsets.UTF_8)
                    + (email.isBlank() ? "" : "&email=" + email);
        }
    }
}
