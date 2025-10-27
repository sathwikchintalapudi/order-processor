package com.trading.trading_order_processor.domain;// ============================================================================
// ORDER - Individual Order Entity
// ============================================================================

public class Order {
    private final String orderId;
    private final String symbol;
    private final String side; // BUY or SELL
    private final double price;
    private int quantity;
    private final String traderId;
    private final long timestamp;
    private String status; // NEW, PARTIAL, FILLED, CANCELLED
    
    public Order(String orderId, String symbol, String side, double price, 
                 int quantity, String traderId, long timestamp) {
        this.orderId = orderId;
        this.symbol = symbol;
        this.side = side;
        this.price = price;
        this.quantity = quantity;
        this.traderId = traderId;
        this.timestamp = timestamp;
        this.status = "NEW";
    }
    
    // Getters and setters
    public String getOrderId() { return orderId; }
    public String getSymbol() { return symbol; }
    public String getSide() { return side; }
    public double getPrice() { return price; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public String getTraderId() { return traderId; }
    public long getTimestamp() { return timestamp; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
