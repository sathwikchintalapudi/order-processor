package com.trading.trading_order_processor;

import com.lmax.disruptor.EventFactory;
import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.lmax.disruptor.YieldingWaitStrategy;
import com.trading.trading_order_processor.domain.OrderEvent;
import com.trading.trading_order_processor.domain.TradeExecution;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Configuration
@Slf4j
public class DisruptorConfig {

    final OrderMatchingEngine orderMatchingEngine;

    private Disruptor<OrderEvent> disruptor; // Keep reference for shutdown

    public DisruptorConfig(OrderMatchingEngine orderMatchingEngine) {
        this.orderMatchingEngine = orderMatchingEngine;
    }


    @Bean
    public RingBuffer<OrderEvent> ringBuffer() {
        // Factory to create events
        EventFactory<OrderEvent> factory = OrderEvent::new;

        // Ring buffer size (must be power of 2)
        int bufferSize = 1024 * 64; // 65,536 slots

        // Create the Disruptor
        disruptor = new Disruptor<>(
                factory,
                bufferSize,
                Executors.defaultThreadFactory(),
                ProducerType.MULTI, // Multiple producers (REST, FIX, WS)
                new YieldingWaitStrategy() // Wait strategy for consumers
        );

        // STEP 4: Define the processing pipeline
        disruptor.handleEventsWith(orderValidator())     // Stage 1: Validate
                .then(riskChecker())                    // Stage 2: Risk check
                .then(orderMatcher(orderMatchingEngine))                   // Stage 3: Match orders
                .then(tradePublisher());                // Stage 4: Publish results

        // Start the disruptor
        disruptor.start();

        return disruptor.getRingBuffer();
    }

    // ============================================================================
    // CRITICAL: Graceful Shutdown
    // ============================================================================

    @PreDestroy
    public void shutdown() {
        if (disruptor != null) {
            log.info("Shutting down Disruptor...");

            try {
                // Shutdown with timeout - allows in-flight events to complete
                disruptor.shutdown(10, TimeUnit.SECONDS);
                log.info("Disruptor shutdown complete");
            } catch (Exception e) {
                log.error("Error during Disruptor shutdown: " + e.getMessage());
                // Force shutdown if graceful shutdown fails
                disruptor.halt();
                log.error("Disruptor halted forcefully");
            }
        }
    }

    @Bean
    public EventHandler<OrderEvent> orderValidator() {
        return (event, sequence, endOfBatch) -> {
            // Validation logic
            if (event.getPrice() <= 0 || event.getQuantity() <= 0) {
                event.setStatus("REJECTED");
                event.setReason("Invalid price or quantity");
            } else {
                event.setStatus("VALIDATED");
            }
            log.info("Validated: " + event.getOrderId() +" | Status: " + event.getStatus());
        };
    }

    @Bean
    public EventHandler<OrderEvent> riskChecker() {
        return (event, sequence, endOfBatch) -> {
            if (!"VALIDATED".equals(event.getStatus())) return;

            // Risk checks: position limits, margin, etc.
            double exposureLimit = 1_000_000.0;
            double currentExposure = event.getPrice() * event.getQuantity();

            if (currentExposure > exposureLimit) {
                event.setStatus("REJECTED");
                event.setReason("Exposure limit exceeded");
            } else {
                event.setStatus("RISK_APPROVED");
            }
            log.info("Risk checked: " + event.getOrderId());
        };
    }

    @Bean
    public EventHandler<OrderEvent> orderMatcher(OrderMatchingEngine matchingEngine) {
        return (event, sequence, endOfBatch) -> {
            if (!"RISK_APPROVED".equals(event.getStatus())) return;

            // Execute matching logic
            List<TradeExecution> trades = matchingEngine.matchOrder(event);

            // Log executions
            if (!trades.isEmpty()) {
                log.info("\n💰 TRADES EXECUTED:");
                trades.forEach(trade -> System.out.println("  " + trade));
            }

            // Print order book state
            System.out.println(matchingEngine.getOrderBookSnapshot(event.getSymbol()));
        };
    }

    @Bean
    public EventHandler<OrderEvent> tradePublisher() {
        return (event, sequence, endOfBatch) -> {
            if (!"MATCHED".equals(event.getStatus())) return;

            long latency = System.nanoTime() - event.getTimestamp();
            long latencyMicros = TimeUnit.NANOSECONDS.toMicros(latency);

            log.info("TRADE EXECUTED: " + event.getOrderId() +
                    " | Latency: " + latency + " microseconds" +
                    " | End of batch: " + endOfBatch);

            // Send to market data feed, notify trader, update positions, etc.
        };
    }
}
