package com.production.domain;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ProductionOrderOperation {
    private Integer operationId;
    private String workCenter;
    private String description;
    private Double setupHours;
    private Double processingHours;
    private Double totalHours;
    private LocalDateTime plannedStart;
    private LocalDateTime plannedFinish;
    private Boolean isDelayed;
    private String delayReason;
}