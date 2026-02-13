package com.example.Gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DailyRateLimitFilter implements GlobalFilter, Ordered {

    private static final Logger logger = LoggerFactory.getLogger(DailyRateLimitFilter.class);

    private static final int DAILY_LIMIT = 50;

    // Store request counts (IP + Date → Count)
    private final ConcurrentHashMap<String, Integer> requestCounts = new ConcurrentHashMap<>();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange,
            GatewayFilterChain chain) {

        // 1️⃣ Get Client IP safely
        String clientIp = "unknown";
        if (exchange.getRequest().getRemoteAddress() != null) {
            clientIp = exchange.getRequest()
                    .getRemoteAddress()
                    .getAddress()
                    .getHostAddress();
        }

        // 2️⃣ Create key (IP + Today Date)
        String key = clientIp + "_" + LocalDate.now();

        // 3️⃣ Get current count
        int currentCount = requestCounts.getOrDefault(key, 0);

        // 4️⃣ If limit exceeded → block request
        if (currentCount >= DAILY_LIMIT) {

            logger.warn("❌ Rate limit exceeded for IP: {} | Requests today: {}",
                    clientIp, currentCount);

            exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            exchange.getResponse().getHeaders()
                    .add("X-RateLimit-Limit", String.valueOf(DAILY_LIMIT));
            exchange.getResponse().getHeaders()
                    .add("X-RateLimit-Remaining", "0");

            return exchange.getResponse().setComplete();
        }

        // 5️⃣ Increase count
        int newCount = currentCount + 1;
        requestCounts.put(key, newCount);

        int remaining = DAILY_LIMIT - newCount;

        // 6️⃣ Add headers to response
        exchange.getResponse().getHeaders()
                .add("X-RateLimit-Limit", String.valueOf(DAILY_LIMIT));
        exchange.getResponse().getHeaders()
                .add("X-RateLimit-Remaining", String.valueOf(remaining));

        // 7️⃣ Log request
        logger.info("✅ IP: {} | Request count today: {} | Remaining: {}",
                clientIp, newCount, remaining);

        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return -1; // Execute before routing
    }
}