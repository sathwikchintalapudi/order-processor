package com.trading.trading_order_processor.domain;

import lombok.Data;

@Data
public class OrderEvent {
    private String orderId;
    private String symbol;
    private String side;
    private double price;
    private int quantity;
    private String traderId;
    private long timestamp;
    private String eventType;
    private String status;
    private String reason;
    private double executionPrice;
}
