package com.weddingraffle.rifa.security;

import com.weddingraffle.rifa.repository.AdminUserRepository;
import java.util.List;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class AdminUserDetailsService implements UserDetailsService {

    private final AdminUserRepository adminUserRepository;

    public AdminUserDetailsService(AdminUserRepository adminUserRepository) {
        this.adminUserRepository = adminUserRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        return adminUserRepository
                .findByUsername(username)
                .map(adminUser -> new User(
                        adminUser.getUsername(),
                        adminUser.getPasswordHash(),
                        List.of(new SimpleGrantedAuthority(
                                "ROLE_" + adminUser.getRole().name()))))
                .orElseThrow(() -> new UsernameNotFoundException("Admin user not found"));
    }
}
