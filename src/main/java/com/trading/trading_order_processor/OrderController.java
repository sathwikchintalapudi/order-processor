package com.trading.trading_order_processor;

import com.trading.trading_order_processor.domain.OrderRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderPublisher orderPublisher;

    public OrderController(OrderPublisher orderPublisher) {
        this.orderPublisher = orderPublisher;
    }

    @PostMapping("/submit")
    public ResponseEntity<String> submitOrder(@RequestBody OrderRequest request) {
        // Data enters here via HTTP POST
        // Example: POST /api/orders/submit
        // Body: {"symbol":"AAPL","side":"BUY","price":150.50,"quantity":100}

        String orderId = orderPublisher.publishOrder(
                request.getSymbol(),
                request.getSide(),
                request.getPrice(),
                request.getQuantity(),
                request.getTraderId()
        );

        return ResponseEntity.ok(orderId);
    }
}
