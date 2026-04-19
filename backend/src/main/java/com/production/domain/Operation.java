package com.production.domain;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Operation {
    private Integer operationId;
    private Double totalHours;          // used by agent
    private String workCenter;          // new
    private String description;         // new
    private Double setupHours;          // new
    private Double processingHours;     // new
    private LocalDateTime plannedStart; // new
    private LocalDateTime plannedFinish;// new
    private Boolean isDelayed;          // new
    private String delayReason;         // new
}