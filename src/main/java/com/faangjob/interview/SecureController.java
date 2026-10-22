package com.faangjob.interview;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SecureController {

    @GetMapping("/api/secure")
    public Map<String, String> secure() {
        return Map.of("message", "JWT validated and access granted");
    }
}