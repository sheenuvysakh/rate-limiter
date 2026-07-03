package org.router.ratelimiter.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.router.ratelimiter.service.RateLimiterService;
import org.router.ratelimiter.service.RateLimiterService.RequestResult;
import org.router.ratelimiter.util.RequestFingerPrinter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/gateway")
public class GatewayController {

    private final RateLimiterService rateLimiterService;

    public GatewayController(RateLimiterService rateLimiterService) {
        this.rateLimiterService = rateLimiterService;
    }

    @GetMapping("/resource")
    public ResponseEntity<String> accessProtectedData(HttpServletRequest request) {
        String clientFingerprint = RequestFingerPrinter.getFingerprint(request);

        RequestResult result = rateLimiterService.evaluateRequest(clientFingerprint);

        return switch (result) {
            case ALLOWED -> ResponseEntity.ok("Success! Request allowed.");
            case DELAYED -> ResponseEntity.ok("Success! Request allowed (Delayed via Tarpit mitigation).");
            case BLOCKED -> ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body("Error: Rate limit exceeded. Hard Ban active.");
        };
    }
}