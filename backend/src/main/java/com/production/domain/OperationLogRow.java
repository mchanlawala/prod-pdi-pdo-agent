package com.production.domain;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class OperationLogRow {
    private String productionOrderId;
    private Integer operationId;
    private LocalDateTime actualStart;
    private LocalDateTime actualFinish;
    private Integer processedQuantity;
    private Integer goodQuantity;
    private Integer scrapQuantity;
    private String scrapReasonCode;
    private Integer downtimeMinutes;
    private String operatorId;
    private String remarks;
}