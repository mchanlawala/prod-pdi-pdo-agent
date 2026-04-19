package com.production.domain;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class PdoRecord {
    private String productionOrderId;
    private Integer operationId;
    private String batchNumber;          // NEW – same as in PDI for the batch
    private Integer goodQuantity;
    private Integer scrapQuantity;
    private String scrapReasonCode;
    private Integer reworkQuantity;
    private Double qualityPassRate;
    private LocalDateTime createdAt;
}