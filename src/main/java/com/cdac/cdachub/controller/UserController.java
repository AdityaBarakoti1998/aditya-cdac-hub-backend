package com.cdac.cdachub.controller;

import com.cdac.cdachub.model.User;
import com.cdac.cdachub.repository.UserRepository;
import com.cdac.cdachub.utils.AuthUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;

    // Who am I?
    @GetMapping("/me")
    public ResponseEntity<User> getCurrentUser() {
        String email = AuthUtil.getCurrentUserEmail();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return ResponseEntity.ok(user);
    }

    //  Admin — get all users
    @GetMapping("/admin/users")
    public ResponseEntity<List<User>> getAllUsers() {
        return ResponseEntity.ok(userRepository.findAll());
    }

    //  Admin — change a user's role
    @PutMapping("/admin/users/{userId}/role")
    public ResponseEntity<?> updateRole(
            @PathVariable Long userId,
            @RequestBody Map<String, String> body) {

        String newRole = body.get("role");

        // Only allow valid roles
        if (!List.of("STUDENT", "REVIEWER", "ADMIN").contains(newRole)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid role: " + newRole));
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // It is for insure admin can not change it own role to STUDENT or REVIEWER
        if (user.getEmail().equals(AuthUtil.getCurrentUserEmail())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Action denied: You cannot change your own role."));
        }
        //here the logic ended
        
       // user.setRole(newRole);
        user.setRole(User.Role.valueOf(newRole));
        // how to fix this error: "The method save(User) in the type UserRepository is not applicable for the arguments (User)"
        //correct it 
        userRepository.save(user);

        return ResponseEntity.ok(Map.of(
            "message", "Role updated successfully",
            "user", user.getEmail(),
            "role", newRole
        ));
    }
    
 // Admin — set which categories a reviewer specializes in
    @PutMapping("/admin/users/{userId}/specializations")
    public ResponseEntity<?> updateSpecializations(
            @PathVariable Long userId,
            @RequestBody Map<String, String> body) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setSpecializations(body.get("specializations"));
        userRepository.save(user);

        return ResponseEntity.ok(Map.of(
            "message", "Specializations updated",
            "user", user.getEmail(),
            "specializations", user.getSpecializations()
        ));
    }
    
}