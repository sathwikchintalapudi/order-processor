package com.trading.trading_order_processor.domain;

import lombok.Data;

@Data
public class OrderRequest {

    private String symbol;
    private String side;
    private double price;
    private int quantity;
    private String traderId;

}
