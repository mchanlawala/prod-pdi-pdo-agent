package com.production.domain;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class PdiRecord {
    private String productionOrderId;
    private Integer operationId;
    private String batchNumber;          // NEW – unique batch identifier
    private LocalDateTime actualStart;
    private LocalDateTime actualFinish;
    private Integer processedQuantity;
    private Integer downtimeSeconds;
    private Boolean autoCreated;
    private String ruleId;
    private LocalDateTime recordedAt;
}