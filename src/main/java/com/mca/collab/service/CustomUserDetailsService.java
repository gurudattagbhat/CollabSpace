package com.mca.collab.service;

import com.mca.collab.model.AppUser;
import com.mca.collab.repository.UserRepository;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AppUser user = userRepository.findByEmail(username.toLowerCase())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        String credential = user.getPassword();
        if (credential == null || credential.isBlank()) {
            credential = user.getLegacyPassword();
        }
        if (credential == null || credential.isBlank()) {
            throw new UsernameNotFoundException("Invalid user credentials");
        }

        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
        return new User(
                user.getEmail(),
                credential,
                user.isEnabled(),
                true,
                true,
                true,
                authorities
        );
    }
}
