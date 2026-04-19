package com.production.domain;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class ProductionOrderResponse {
    private List<ProductionOrder> productionOrders;
    private Map<String, Object> summary;
    private String aiInsights;
}