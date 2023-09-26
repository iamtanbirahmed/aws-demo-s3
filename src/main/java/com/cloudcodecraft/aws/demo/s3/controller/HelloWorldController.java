package com.cloudcodecraft.aws.demo.s3.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@EnableMethodSecurity
@RequestMapping("")
public class HelloWorldController {

    @GetMapping("/health/check")
    public ResponseEntity<?> healthCheck() {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body("Service is Healthy");
    }

    @GetMapping("/security/check")
    public ResponseEntity<?> securityCheck(Authentication authentication) {
        String welcome = "Welcome" + "\n" + authentication.getName() + "\n" + authentication.getAuthorities().toString();
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body("Service is Secured" + "\n" + welcome);
    }
}
