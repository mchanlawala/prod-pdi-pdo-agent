package com.production.domain;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class AlertRecord {
    private String id;
    private String productionOrderId;
    private Integer operationId;
    private String type;
    private String message;
    private Boolean acknowledged;
    private LocalDateTime createdAt;
}