package com.production.domain;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class ProductionOrder {
    private String productionOrderId;
    private String salesOrderId;
    private String material;
    private Integer plannedQuantity;
    private LocalDateTime basicStartDate;
    private LocalDateTime basicFinishDate;
    private LocalDateTime actualStartDate;
    private LocalDateTime actualFinishDate;
    private String status;               // RELEASED, DELAYED, CREATED
    private List<Operation> operations;
    private List<ProductionOrderComponent> components;
    private Long totalLeadTimeMinutes;
    private String delayReason;
}