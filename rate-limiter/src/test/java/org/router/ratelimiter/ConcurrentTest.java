package org.router.ratelimiter;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class ConcurrentTest {
    public static void main(String[] args) throws InterruptedException {
        var allowed = new AtomicInteger();
        var blocked = new AtomicInteger();

        // Spin up 20 genuine concurrent virtual threads
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 20; i++) {
                executor.submit(() -> {
                    try (var client = HttpClient.newHttpClient()) {
                        var request = HttpRequest.newBuilder()
                                .uri(URI.create("http://localhost:8080/api/v1/gateway/resource"))
                                .header("X-API-Key", "someone-999")
                                .build();

                        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
                        if (response.statusCode() == 200) {
                            allowed.incrementAndGet();
                        } else {
                            blocked.incrementAndGet();
                        }
                    } catch (Exception e) {
                        blocked.incrementAndGet();
                    }
                });
            }
        } // Wait for all virtual threads to finish instantly

        System.out.println("--- True Java Concurrency Results ---");
        System.out.println("Allowed: " + allowed.get() + " (Should be exactly 5)");
        System.out.println("Blocked: " + blocked.get() + " (Should be exactly 15)");
    }
}
