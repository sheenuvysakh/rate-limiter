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
        var delayed = new AtomicInteger();
        var blocked = new AtomicInteger();

        System.out.println("Firing 20 concurrent requests simultaneously");
        long startTime = System.currentTimeMillis();

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 1; i <= 20; i++) {
                final int requestId = i;
                executor.submit(() -> {
                    long requestStart = System.currentTimeMillis();
                    try (var client = HttpClient.newHttpClient()) {
                        var request = HttpRequest.newBuilder()
                                .uri(URI.create("http://localhost:8080/api/v1/gateway/resource"))
                                .header("User-Agent", "JustAnotherBot/v1.0") // Static signature
                                .build();

                        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
                        long duration = System.currentTimeMillis() - requestStart;

                        if (response.statusCode() == 200) {
                            if (response.body().contains("Delayed")) {
                                delayed.incrementAndGet();
                                System.out.printf("[TASK %02d] Tarpit Hit! Response code 200 but took %d ms%n", requestId, duration);
                            } else {
                                allowed.incrementAndGet();
                                System.out.printf("[TASK %02d] Clean Pass! Response code 200 in %d ms%n", requestId, duration);
                            }
                        } else if (response.statusCode() == 429) {
                            blocked.incrementAndGet();
                            System.out.printf("[TASK %02d] Hard Blocked! Response code 429 in %d ms%n", requestId, duration);
                        }
                    } catch (Exception e) {
                        System.out.println("Request failed: " + e.getMessage());
                    }
                });
            }
        }

        long totalTime = System.currentTimeMillis() - startTime;
        System.out.println("\n=== FINAL BENCHMARK METRICS ===");
        System.out.println("Total Execution Time: " + totalTime + " ms");
        System.out.println("Clean Passes (1-5):   " + allowed.get() + " (Expected 5)");
        System.out.println("Tarpitted (6-10):     " + delayed.get() + " (Expected 5)");
        System.out.println("Hard Blocked (11-20): " + blocked.get() + " (Expected 10)");
    }
}