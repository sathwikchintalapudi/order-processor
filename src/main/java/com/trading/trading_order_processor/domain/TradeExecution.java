package com.trading.trading_order_processor.domain;

// ============================================================================
// TRADE EXECUTION - Result of Matching
// ============================================================================

public class TradeExecution {
    private final String tradeId;
    private final String buyOrderId;
    private final String sellOrderId;
    private final String symbol;
    private final double executionPrice;
    private final int executionQuantity;
    private final long timestamp;
    private final String buyTraderId;
    private final String sellTraderId;
    
    public TradeExecution(String tradeId, String buyOrderId, String sellOrderId,
                         String symbol, double executionPrice, int executionQuantity,
                         String buyTraderId, String sellTraderId) {
        this.tradeId = tradeId;
        this.buyOrderId = buyOrderId;
        this.sellOrderId = sellOrderId;
        this.symbol = symbol;
        this.executionPrice = executionPrice;
        this.executionQuantity = executionQuantity;
        this.timestamp = System.nanoTime();
        this.buyTraderId = buyTraderId;
        this.sellTraderId = sellTraderId;
    }
    
    @Override
    public String toString() {
        return String.format("TRADE[%s] %s: %d @ %.2f (Buy:%s, Sell:%s)", 
            tradeId, symbol, executionQuantity, executionPrice, 
            buyOrderId, sellOrderId);
    }
    
    // Getters
    public String getTradeId() { return tradeId; }
    public String getBuyOrderId() { return buyOrderId; }
    public String getSellOrderId() { return sellOrderId; }
    public String getSymbol() { return symbol; }
    public double getExecutionPrice() { return executionPrice; }
    public int getExecutionQuantity() { return executionQuantity; }
    public long getTimestamp() { return timestamp; }
}