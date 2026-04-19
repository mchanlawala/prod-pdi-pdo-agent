package com.production.domain;

import lombok.Data;

@Data
public class ProductionOrderComponent {
    private String componentCode;
    private String componentDescription;
    private Double requiredQuantity;
    private Double reservedQuantity;
    private String unit;
    private String batch;
    private Boolean withdrawn;
}