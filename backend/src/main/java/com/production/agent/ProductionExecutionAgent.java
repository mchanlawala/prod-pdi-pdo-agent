package com.production.agent;

import com.production.domain.*;
import com.production.service.ExecutionFileStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductionExecutionAgent {

    private final ExecutionFileStorage storage;
    private final Map<String, Integer> batchCounter = new ConcurrentHashMap<>();

    // ================== CONFIGURABLE MAX BATCH SIZES (customise for your factory) ==================
    private static final Map<String, Integer> WORK_CENTER_MAX_BATCH = Map.of(
            "CELL-TEST-01", Integer.MAX_VALUE,   // no practical limit
            "LASER-01", Integer.MAX_VALUE,
            "STRINGER-02", Integer.MAX_VALUE,    // continuous, batch defined by operator
            "EL-TEST-01", 1000,
            "LAMINATOR-03", 1000,               // critical constraint
            "FRAMING-01", Integer.MAX_VALUE,
            "FLASH-TEST-01", 500,               // typical flash tester capacity
            "EL-TEST-02", 1000,
            "PACK-01", 30                       // pallet size
    );

    public void processOperationLog(OperationLogRow operationLog, List<ProductionOrder> productionOrders) {
        log.info("Processing: Order={}, Op={}", operationLog.getProductionOrderId(), operationLog.getOperationId());

        // Find the production order and the specific operation
        ProductionOrder order = findOrder(productionOrders, operationLog.getProductionOrderId());
        if (order == null) {
            log.error("Order not found: {}", operationLog.getProductionOrderId());
            return;
        }
        var operation = order.getOperations().stream()
                .filter(op -> op.getOperationId().equals(operationLog.getOperationId()))
                .findFirst().orElse(null);
        if (operation == null) {
            log.error("Operation {} not found in order {}", operationLog.getOperationId(), operationLog.getProductionOrderId());
            return;
        }

        String workCenter = operation.getWorkCenter();
        int maxBatch = WORK_CENTER_MAX_BATCH.getOrDefault(workCenter, Integer.MAX_VALUE);

        int totalProcessed = operationLog.getProcessedQuantity();
        int totalGood = operationLog.getGoodQuantity();
        int totalScrap = operationLog.getScrapQuantity();
        // Sanity check
        if (totalProcessed != totalGood + totalScrap) {
            log.warn("Inconsistent data: processed={}, good={}, scrap={}. Adjusting.", totalProcessed, totalGood, totalScrap);
            totalGood = totalProcessed - totalScrap;
        }

        // Determine number of batches
        int numBatches = (int) Math.ceil((double) totalProcessed / maxBatch);
        int remainingProcessed = totalProcessed;
        int remainingGood = totalGood;
        int remainingScrap = totalScrap;

        List<String> batchNumbers = new ArrayList<>();

        for (int batchIdx = 1; batchIdx <= numBatches; batchIdx++) {
            int batchProcessed = Math.min(remainingProcessed, maxBatch);
            // Distribute good/scrap proportionally (last batch gets remainder)
            int batchGood = (int) Math.round((double) batchProcessed / totalProcessed * totalGood);
            int batchScrap = batchProcessed - batchGood;
            if (batchIdx == numBatches) {
                batchGood = remainingGood;
                batchScrap = remainingScrap;
            }
            remainingProcessed -= batchProcessed;
            remainingGood -= batchGood;
            remainingScrap -= batchScrap;

            String batchNumber = generateBatchNumber(operationLog.getProductionOrderId(), operationLog.getOperationId());
            batchNumbers.add(batchNumber);

            // Create PDI record for this batch (timestamps are the same for all batches – can be refined)
            PdiRecord pdi = buildPdiRecord(operationLog, batchNumber, batchProcessed);
            storage.addPdi(pdi);

            // Create PDO record for this batch
            PdoRecord pdo = buildPdoRecord(operationLog, batchNumber, batchGood, batchScrap);
            storage.addPdo(pdo);

            // Scrap alert per batch (if scrap rate > 2%)
            double scrapRate = (double) batchScrap / batchProcessed * 100;
            if (scrapRate > 2.0) {
                AlertRecord alert = new AlertRecord();
                alert.setId(UUID.randomUUID().toString());
                alert.setProductionOrderId(operationLog.getProductionOrderId());
                alert.setOperationId(operationLog.getOperationId());
                alert.setType("SCRAP_ALERT");
                alert.setMessage(String.format("Batch %s: scrap rate %.2f%% exceeds 2%% (%d scrap / %d processed)",
                        batchNumber, scrapRate, batchScrap, batchProcessed));
                alert.setAcknowledged(false);
                alert.setCreatedAt(LocalDateTime.now());
                storage.addAlert(alert);
            }
        }

        // Cycle time alert (only once for the whole row – optional)
        double plannedHours = operation.getTotalHours();
        double actualHours = Duration.between(operationLog.getActualStart(), operationLog.getActualFinish()).toMinutes() / 60.0;
        if (actualHours > plannedHours * 2) {
            AlertRecord alert = new AlertRecord();
            alert.setId(UUID.randomUUID().toString());
            alert.setType("CYCLE_TIME_ALERT");
            alert.setMessage(String.format("Op %d actual %.2fh > 2× planned %.2fh (order %s)",
                    operationLog.getOperationId(), actualHours, plannedHours, operationLog.getProductionOrderId()));
            alert.setAcknowledged(false);
            alert.setCreatedAt(LocalDateTime.now());
            storage.addAlert(alert);
        }

        // Update production order remaining quantity (once after all batches)
        int newRemaining = order.getPlannedQuantity() - totalGood;
        if (newRemaining < 0) newRemaining = 0;
        order.setPlannedQuantity(newRemaining);
        try {
            storage.saveProductionOrders(productionOrders);
        } catch (Exception e) {
            log.error("Failed to save production orders", e);
        }
    }

    private String generateBatchNumber(String orderId, int operationId) {
        String key = orderId + "-" + operationId;
        int seq = batchCounter.getOrDefault(key, 0) + 1;
        batchCounter.put(key, seq);
        return "BATCH-" + orderId + "-" + operationId + "-" + seq;
    }

    private PdiRecord buildPdiRecord(OperationLogRow operationLog, String batchNumber, int batchProcessed) {
        PdiRecord pdi = new PdiRecord();
        pdi.setProductionOrderId(operationLog.getProductionOrderId());
        pdi.setOperationId(operationLog.getOperationId());
        pdi.setBatchNumber(batchNumber);
        pdi.setActualStart(operationLog.getActualStart());
        pdi.setActualFinish(operationLog.getActualFinish());
        pdi.setProcessedQuantity(batchProcessed);
        pdi.setDowntimeSeconds(operationLog.getDowntimeMinutes() * 60);
        pdi.setAutoCreated(false);
        pdi.setRecordedAt(LocalDateTime.now());
        return pdi;
    }

    private PdoRecord buildPdoRecord(OperationLogRow operationLog, String batchNumber, int batchGood, int batchScrap) {
        PdoRecord pdo = new PdoRecord();
        pdo.setProductionOrderId(operationLog.getProductionOrderId());
        pdo.setOperationId(operationLog.getOperationId());
        pdo.setBatchNumber(batchNumber);
        pdo.setGoodQuantity(batchGood);
        pdo.setScrapQuantity(batchScrap);
        pdo.setScrapReasonCode(operationLog.getScrapReasonCode());
        pdo.setReworkQuantity(0);
        double passRate = (batchGood + batchScrap) > 0 ? (double) batchGood / (batchGood + batchScrap) * 100 : 100;
        pdo.setQualityPassRate(passRate);
        pdo.setCreatedAt(LocalDateTime.now());
        return pdo;
    }

    private ProductionOrder findOrder(List<ProductionOrder> orders, String id) {
        return orders.stream().filter(o -> o.getProductionOrderId().equals(id)).findFirst().orElse(null);
    }
}