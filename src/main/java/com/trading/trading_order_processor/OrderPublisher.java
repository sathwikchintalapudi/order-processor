package com.trading.trading_order_processor;

import com.lmax.disruptor.RingBuffer;
import com.trading.trading_order_processor.domain.OrderEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@Slf4j
public class OrderPublisher {

    private final RingBuffer<OrderEvent> ringBuffer;

    public OrderPublisher(RingBuffer<OrderEvent> ringBuffer) {
        this.ringBuffer = ringBuffer;
    }

    public String publishOrder(String symbol, String side, double price,
                               int quantity, String traderId) {

        String orderId = UUID.randomUUID().toString();

        // Get next available slot in ring buffer (this is the critical step!)
        long sequence = ringBuffer.next();

        try {
            // Get the event object at this sequence
            OrderEvent event = ringBuffer.get(sequence);

            // Populate the event with order data
            event.setOrderId(orderId);
            event.setSymbol(symbol);
            event.setSide(side);
            event.setPrice(price);
            event.setQuantity(quantity);
            event.setTraderId(traderId);
            event.setTimestamp(System.nanoTime());
            event.setEventType("NEW_ORDER");


        } finally {
            // Publish the event (makes it visible to consumers)
            ringBuffer.publish(sequence);
            log.info("Published to Disruptor: " + orderId + " at sequence: " + sequence);
        }

        return orderId;
    }

}
