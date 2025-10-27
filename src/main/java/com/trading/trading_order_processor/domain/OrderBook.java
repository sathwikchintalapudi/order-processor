package com.trading.trading_order_processor.domain;

import java.util.Collections;
import java.util.Map;
import java.util.Queue;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Order Book maintains buy and sell orders for a single symbol
 * Uses TreeMap for O(log n) price-time priority matching
 */
public class OrderBook {
    
    private final String symbol;
    
    // Buy orders: Highest price first (descending order)
    private final TreeMap<Double, Queue<Order>> buyOrders = 
        new TreeMap<>(Collections.reverseOrder());
    
    // Sell orders: Lowest price first (ascending order)
    private final TreeMap<Double, Queue<Order>> sellOrders = 
        new TreeMap<>();
    
    // Track all orders for cancellation/modification
    private final Map<String, Order> orderRegistry = new ConcurrentHashMap<>();
    
    public OrderBook(String symbol) {
        this.symbol = symbol;
    }
    
    public String getSymbol() {
        return symbol;
    }
    
    public TreeMap<Double, Queue<Order>> getBuyOrders() {
        return buyOrders;
    }
    
    public TreeMap<Double, Queue<Order>> getSellOrders() {
        return sellOrders;
    }
    
    public Map<String, Order> getOrderRegistry() {
        return orderRegistry;
    }
}