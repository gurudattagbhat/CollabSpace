package com.mca.collab.config;

import com.mca.collab.repository.UserRepository;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.security.Principal;

@ControllerAdvice
public class GlobalModelAttributes {

    private final UserRepository userRepository;

    public GlobalModelAttributes(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @ModelAttribute("isAuthenticated")
    public boolean isAuthenticated(Principal principal) {
        return principal != null;
    }

    @ModelAttribute("displayName")
    public String displayName(Principal principal) {
        if (principal == null) {
            return null;
        }

        return userRepository.findByEmail(principal.getName())
                .map(user -> {
                    if (user.getDisplayName() != null && !user.getDisplayName().isBlank()) {
                        return user.getDisplayName();
                    }
                    return principal.getName();
                })
                .orElse(principal.getName());
    }
}
