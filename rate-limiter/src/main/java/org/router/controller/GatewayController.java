package org.router.controller;

import org.router.service.RateLimiterService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/gateway")
public class GatewayController {

    private static final Logger log = LoggerFactory.getLogger(GatewayController.class);
    private final RateLimiterService rateLimiterService;

    public GatewayController(RateLimiterService rateLimiterService) {
        this.rateLimiterService = rateLimiterService;
    }

    @GetMapping("/resource")
    public ResponseEntity<String> accessProtectedData(@RequestHeader(value = "X-API-Key", defaultValue = "anonymous") String apiKey) {
        log.info("Entered");
        if (rateLimiterService.isAllowed(apiKey)) {
            return ResponseEntity.ok("Success! Request allowed through edge gateway.");
        }

        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body("Error: Rate limit exceeded. Try again in a few seconds.");
    }
}
