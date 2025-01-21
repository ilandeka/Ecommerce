package com.eshop.security;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class SecuredController {
    /*
     *
     * implement Role-Based Authentication
     * when a client accesses an endpoint authorize his Role first!
     *
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin/users")
    public List<UserDTO> getUsers() {
        // Only accessible by admins
        return userService.getAllUsers();
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @GetMapping("/reports")
    public List<ReportDTO> getReports() {
        // Accessible by both admins and managers
        return reportService.getReports();
    }

    @PreAuthorize("#userId == authentication.principal.id")
    @GetMapping("/users/{userId}/profile")
    public UserProfile getUserProfile(@PathVariable Long userId) {
        // Only accessible by the user themselves
        return userService.getUserProfile(userId);
    }
     */
}
