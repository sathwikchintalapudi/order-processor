package com.trading.trading_order_processor;

import com.trading.trading_order_processor.domain.Order;
import com.trading.trading_order_processor.domain.OrderBook;
import com.trading.trading_order_processor.domain.OrderEvent;
import com.trading.trading_order_processor.domain.TradeExecution;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class OrderMatchingEngine {
    
    // Maintain separate order book for each symbol
    private final Map<String, OrderBook> orderBooks = new ConcurrentHashMap<>();
    private final AtomicLong tradeIdGenerator = new AtomicLong(0);
    
    /**
     * Main matching logic - called from Disruptor event handler
     * 
     * ALGORITHM: Price-Time Priority Matching
     * 1. For BUY order: Match with lowest priced SELL orders
     * 2. For SELL order: Match with highest priced BUY orders
     * 3. Within same price level: FIFO (First-In-First-Out)
     */
    public List<TradeExecution> matchOrder(OrderEvent event) {
        
        List<TradeExecution> executions = new ArrayList<>();
        
        // Get or create order book for this symbol
        OrderBook book = orderBooks.computeIfAbsent(
            event.getSymbol(), 
            k -> new OrderBook(event.getSymbol())
        );
        
        // Create order object
        Order incomingOrder = new Order(
            event.getOrderId(),
            event.getSymbol(),
            event.getSide(),
            event.getPrice(),
            event.getQuantity(),
            event.getTraderId(),
            event.getTimestamp()
        );
        
        System.out.println("\n📋 Processing Order: " + incomingOrder.getOrderId() + 
                         " | " + incomingOrder.getSide() + " " + 
                         incomingOrder.getQuantity() + " @ " + incomingOrder.getPrice());
        
        // Match based on side
        if ("BUY".equals(incomingOrder.getSide())) {
            executions = matchBuyOrder(book, incomingOrder);
        } else if ("SELL".equals(incomingOrder.getSide())) {
            executions = matchSellOrder(book, incomingOrder);
        }
        
        // If order not fully filled, add remaining quantity to book
        if (incomingOrder.getQuantity() > 0) {
            addOrderToBook(book, incomingOrder);
            System.out.println("  → Remaining " + incomingOrder.getQuantity() + 
                             " added to order book");
        }
        
        // Update event with execution details
        if (!executions.isEmpty()) {
            event.setStatus("MATCHED");
            event.setExecutionPrice(executions.get(0).getExecutionPrice());
        } else {
            event.setStatus("PENDING"); // No match found, waiting in book
        }
        
        return executions;
    }
    
    /**
     * STEP 1: Match incoming BUY order against existing SELL orders
     * 
     * Logic:
     * - Look at SELL side (want lowest prices)
     * - Match if: buyPrice >= sellPrice
     * - Execution price: sellPrice (maker's price)
     */
    private List<TradeExecution> matchBuyOrder(OrderBook book, Order buyOrder) {
        
        List<TradeExecution> executions = new ArrayList<>();
        TreeMap<Double, Queue<Order>> sellSide = book.getSellOrders();
        
        System.out.println("  🔍 Checking SELL side for matches...");
        System.out.println("  📊 Best SELL price: " + 
            (sellSide.isEmpty() ? "N/A" : sellSide.firstKey()));
        
        // Iterate through sell orders from lowest to highest price
        while (buyOrder.getQuantity() > 0 && !sellSide.isEmpty()) {
            
            // Get best (lowest) sell price
            Map.Entry<Double, Queue<Order>> bestSell = sellSide.firstEntry();
            double sellPrice = bestSell.getKey();
            
            // Check if prices match: BUY price >= SELL price
            if (buyOrder.getPrice() < sellPrice) {
                System.out.println("  ❌ No match: Buy price " + buyOrder.getPrice() + 
                                 " < Sell price " + sellPrice);
                break; // No more matches possible
            }
            
            // Get queue of orders at this price level
            Queue<Order> ordersAtPrice = bestSell.getValue();
            
            // Match with first order in queue (FIFO)
            Order sellOrder = ordersAtPrice.peek();
            
            if (sellOrder == null) {
                // Remove empty price level
                sellSide.remove(sellPrice);
                continue;
            }
            
            // Calculate trade quantity (minimum of both)
            int tradeQty = Math.min(buyOrder.getQuantity(), sellOrder.getQuantity());
            
            // Execution price is the maker's price (sell order was there first)
            double executionPrice = sellPrice;
            
            System.out.println("  ✅ MATCH FOUND!");
            System.out.println("    Buy Order:  " + buyOrder.getOrderId() + " (" + 
                             buyOrder.getQuantity() + " @ " + buyOrder.getPrice() + ")");
            System.out.println("    Sell Order: " + sellOrder.getOrderId() + " (" + 
                             sellOrder.getQuantity() + " @ " + sellOrder.getPrice() + ")");
            System.out.println("    Executing:  " + tradeQty + " @ " + executionPrice);
            
            // Create trade execution
            TradeExecution trade = new TradeExecution(
                "TRD-" + tradeIdGenerator.incrementAndGet(),
                buyOrder.getOrderId(),
                sellOrder.getOrderId(),
                buyOrder.getSymbol(),
                executionPrice,
                tradeQty,
                buyOrder.getTraderId(),
                sellOrder.getTraderId()
            );
            executions.add(trade);
            
            // Update quantities
            buyOrder.setQuantity(buyOrder.getQuantity() - tradeQty);
            sellOrder.setQuantity(sellOrder.getQuantity() - tradeQty);
            
            // Update order statuses
            if (buyOrder.getQuantity() == 0) {
                buyOrder.setStatus("FILLED");
                System.out.println("    Buy order FULLY FILLED");
            } else {
                buyOrder.setStatus("PARTIAL");
                System.out.println("    Buy order PARTIALLY FILLED (" + 
                                 buyOrder.getQuantity() + " remaining)");
            }
            
            if (sellOrder.getQuantity() == 0) {
                sellOrder.setStatus("FILLED");
                ordersAtPrice.poll(); // Remove from queue
                book.getOrderRegistry().remove(sellOrder.getOrderId());
                System.out.println("    Sell order FULLY FILLED (removed from book)");
            } else {
                sellOrder.setStatus("PARTIAL");
                System.out.println("    Sell order PARTIALLY FILLED (" + 
                                 sellOrder.getQuantity() + " remaining)");
            }
            
            // Remove price level if no more orders
            if (ordersAtPrice.isEmpty()) {
                sellSide.remove(sellPrice);
                System.out.println("    Price level " + sellPrice + " cleared");
            }
        }
        
        return executions;
    }
    
    /**
     * STEP 2: Match incoming SELL order against existing BUY orders
     * 
     * Logic:
     * - Look at BUY side (want highest prices)
     * - Match if: sellPrice <= buyPrice
     * - Execution price: buyPrice (maker's price)
     */
    private List<TradeExecution> matchSellOrder(OrderBook book, Order sellOrder) {
        
        List<TradeExecution> executions = new ArrayList<>();
        TreeMap<Double, Queue<Order>> buySide = book.getBuyOrders();
        
        System.out.println("  🔍 Checking BUY side for matches...");
        System.out.println("  📊 Best BUY price: " + 
            (buySide.isEmpty() ? "N/A" : buySide.firstKey()));
        
        // Iterate through buy orders from highest to lowest price
        while (sellOrder.getQuantity() > 0 && !buySide.isEmpty()) {
            
            // Get best (highest) buy price
            Map.Entry<Double, Queue<Order>> bestBuy = buySide.firstEntry();
            double buyPrice = bestBuy.getKey();
            
            // Check if prices match: SELL price <= BUY price
            if (sellOrder.getPrice() > buyPrice) {
                System.out.println("  ❌ No match: Sell price " + sellOrder.getPrice() + 
                                 " > Buy price " + buyPrice);
                break;
            }
            
            Queue<Order> ordersAtPrice = bestBuy.getValue();
            Order buyOrder = ordersAtPrice.peek();
            
            if (buyOrder == null) {
                buySide.remove(buyPrice);
                continue;
            }
            
            int tradeQty = Math.min(sellOrder.getQuantity(), buyOrder.getQuantity());
            double executionPrice = buyPrice; // Maker's price
            
            System.out.println("  ✅ MATCH FOUND!");
            System.out.println("    Sell Order: " + sellOrder.getOrderId() + " (" + 
                             sellOrder.getQuantity() + " @ " + sellOrder.getPrice() + ")");
            System.out.println("    Buy Order:  " + buyOrder.getOrderId() + " (" + 
                             buyOrder.getQuantity() + " @ " + buyOrder.getPrice() + ")");
            System.out.println("    Executing:  " + tradeQty + " @ " + executionPrice);
            
            TradeExecution trade = new TradeExecution(
                "TRD-" + tradeIdGenerator.incrementAndGet(),
                buyOrder.getOrderId(),
                sellOrder.getOrderId(),
                sellOrder.getSymbol(),
                executionPrice,
                tradeQty,
                buyOrder.getTraderId(),
                sellOrder.getTraderId()
            );
            executions.add(trade);
            
            // Update quantities and statuses
            sellOrder.setQuantity(sellOrder.getQuantity() - tradeQty);
            buyOrder.setQuantity(buyOrder.getQuantity() - tradeQty);
            
            if (sellOrder.getQuantity() == 0) {
                sellOrder.setStatus("FILLED");
                System.out.println("    Sell order FULLY FILLED");
            } else {
                sellOrder.setStatus("PARTIAL");
                System.out.println("    Sell order PARTIALLY FILLED (" + 
                                 sellOrder.getQuantity() + " remaining)");
            }
            
            if (buyOrder.getQuantity() == 0) {
                buyOrder.setStatus("FILLED");
                ordersAtPrice.poll();
                book.getOrderRegistry().remove(buyOrder.getOrderId());
                System.out.println("    Buy order FULLY FILLED (removed from book)");
            } else {
                buyOrder.setStatus("PARTIAL");
                System.out.println("    Buy order PARTIALLY FILLED (" + 
                                 buyOrder.getQuantity() + " remaining)");
            }
            
            if (ordersAtPrice.isEmpty()) {
                buySide.remove(buyPrice);
                System.out.println("    Price level " + buyPrice + " cleared");
            }
        }
        
        return executions;
    }
    
    /**
     * STEP 3: Add unfilled order to order book
     */
    private void addOrderToBook(OrderBook book, Order order) {
        
        TreeMap<Double, Queue<Order>> targetSide = 
            "BUY".equals(order.getSide()) ? book.getBuyOrders() : book.getSellOrders();
        
        // Get or create queue for this price level
        Queue<Order> ordersAtPrice = targetSide.computeIfAbsent(
            order.getPrice(), 
            k -> new LinkedList<>()
        );
        
        // Add to end of queue (FIFO)
        ordersAtPrice.add(order);
        
        // Register order for lookups
        book.getOrderRegistry().put(order.getOrderId(), order);
        
        System.out.println("  📚 Order added to book: " + order.getOrderId() + 
                         " | " + order.getSide() + " " + order.getQuantity() + 
                         " @ " + order.getPrice());
    }
    
    /**
     * Get current state of order book (for monitoring/debugging)
     */
    public String getOrderBookSnapshot(String symbol) {
        OrderBook book = orderBooks.get(symbol);
        if (book == null) return "No order book for " + symbol;
        
        StringBuilder sb = new StringBuilder();
        sb.append("\n╔════════════════════════════════════════╗\n");
        sb.append("║  ORDER BOOK: ").append(symbol).append("\n");
        sb.append("╠════════════════════════════════════════╣\n");
        
        // Sell side (ascending)
        sb.append("║  SELL SIDE (Ask)\n");
        book.getSellOrders().forEach((price, orders) -> {
            int totalQty = orders.stream().mapToInt(Order::getQuantity).sum();
            sb.append(String.format("║    %.2f  x%d  (%d orders)\n", 
                price, totalQty, orders.size()));
        });
        
        sb.append("║  ────────────────────────\n");
        
        // Buy side (descending)
        sb.append("║  BUY SIDE (Bid)\n");
        book.getBuyOrders().forEach((price, orders) -> {
            int totalQty = orders.stream().mapToInt(Order::getQuantity).sum();
            sb.append(String.format("║    %.2f  x%d  (%d orders)\n", 
                price, totalQty, orders.size()));
        });
        
        sb.append("╚════════════════════════════════════════╝\n");
        return sb.toString();
    }
}