# Multi-Tier Rate Limiter & Tarpit Gateway

This is an API rate-limiting gateway built with **Spring Boot** and **Java 25**. It stops high-velocity bot traffic, scrapers, and brute-force scripts before they can hit your database or core business logic. 

Instead of just blocking requests blindly, it includes an automated **Tarpit delay layer** to slow attackers down and intentionally exhaust their local computing resources.

---

## The Stack

* **Java 25** – Uses Virtual Threads to handle connection stalling at scale without crashing server memory.
* **Spring Boot** – Handles the web framework and routing components.
* **Redis** – Used for tracking distributed traffic counts and storing active bans across cluster instances.
* **Lua Scripting** – Keeps the tracking, scaling, and ban operations 100% atomic inside Redis to eliminate race conditions under heavy load.

---

## How the Traffic Enforcement Works

The application tracks incoming clients using a structural fingerprint (a `SHA-256` hash of `IP + User-Agent`) or reads upstream edge headers like Cloudflare's `CF-JA4`. 

Inside a sliding **2-second tracking window**, traffic falls into three progressive defense tiers:

* **1 to 5 requests (Clean Pass):** Allowed through instantly like normal human traffic.
* **6 to 10 requests (The Tarpit):** The request stays open, and the gateway forces a deliberate **3-second execution stall** before letting it pass. Because the project uses **Java 25 Virtual Threads**, these sleeping connections cost almost zero server memory, but they quickly lock up and exhaust the attacker's automation tools.
* **11+ requests (The Hard Ban):** If the client continues to spam through the delay, a **10-minute ban** key is written to Redis. For the next 10 minutes, any incoming request carrying that fingerprint is instantly blocked with an `HTTP 429` in under a single millisecond.

---
