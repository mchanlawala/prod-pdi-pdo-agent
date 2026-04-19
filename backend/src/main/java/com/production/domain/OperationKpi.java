package com.production.domain;

import lombok.Data;

@Data
public class OperationKpi {
    private Integer operationId;
    private String workCenter;
    private String description;
    private int totalProcessed;
    private int totalGood;
    private int totalScrap;
    private double avgQualityRate;
    private long totalDowntimeSeconds;
    private int totalAlerts;
}