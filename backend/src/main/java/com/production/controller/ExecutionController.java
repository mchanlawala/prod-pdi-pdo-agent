package com.production.controller;

import com.production.agent.ExecutionInsightsAgent;
import com.production.agent.ProductionExecutionAgent;
import com.production.domain.*;
import com.production.service.ExcelExecutionParser;
import com.production.service.ExecutionFileStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/execution")
@RequiredArgsConstructor
public class ExecutionController {

    private final ExcelExecutionParser parser;
    private final ProductionExecutionAgent agent;
    private final ExecutionFileStorage storage;
    private final ExecutionInsightsAgent insightsAgent;

    // ONLY ONE upload method – the enhanced one
    @PostMapping("/upload")
    public ResponseEntity<?> uploadExecutionLog(@RequestParam("file") MultipartFile file) {
        try {
            List<OperationLogRow> logs = parser.parse(file);
            List<ProductionOrder> productionOrders = storage.loadProductionOrders();
            if (productionOrders.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "No production orders found. Please upload production_orders.json to ./data/ folder first."));
            }
            for (OperationLogRow log : logs) {
                agent.processOperationLog(log, productionOrders);
            }

            // Build summary
            Map<String, Object> summary = buildSummary(productionOrders);

            // Get AI insights
            String insights = insightsAgent.analyseExecution(
                    storage.getPdiRecords(),
                    storage.getPdoRecords(),
                    storage.getAlerts()
            );

            ProductionOrderResponse response = new ProductionOrderResponse();
            response.setProductionOrders(productionOrders);
            response.setSummary(summary);
            response.setAiInsights(insights);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Upload error", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/pdi")
    public ResponseEntity<List<PdiRecord>> getPdiRecords() {
        return ResponseEntity.ok(storage.getPdiRecords());
    }

    @GetMapping("/pdo")
    public ResponseEntity<List<PdoRecord>> getPdoRecords() {
        return ResponseEntity.ok(storage.getPdoRecords());
    }

    @GetMapping("/alerts")
    public ResponseEntity<List<AlertRecord>> getAlerts() {
        return ResponseEntity.ok(storage.getAlerts());
    }

    @PostMapping("/alerts/acknowledge/{id}")
    public ResponseEntity<Void> acknowledgeAlert(@PathVariable String id) {
        List<AlertRecord> alerts = storage.getAlerts();
        for (AlertRecord alert : alerts) {
            if (alert.getId().equals(id)) {
                alert.setAcknowledged(true);
                storage.updateAlert(alert);
                break;
            }
        }
        return ResponseEntity.ok().build();
    }

    @GetMapping("/insights")
    public ResponseEntity<Map<String, String>> getInsights() {
        List<PdiRecord> pdi = storage.getPdiRecords();
        List<PdoRecord> pdo = storage.getPdoRecords();
        List<AlertRecord> alerts = storage.getAlerts();
        String insights = insightsAgent.analyseExecution(pdi, pdo, alerts);
        return ResponseEntity.ok(Map.of("insights", insights));
    }

    @GetMapping("/kpi/operation")
    public ResponseEntity<List<OperationKpi>> getOperationKpis() {
        List<PdoRecord> pdoRecords = storage.getPdoRecords();
        List<PdiRecord> pdiRecords = storage.getPdiRecords();
        List<AlertRecord> alerts = storage.getAlerts();

        Map<Integer, OperationKpi> kpiMap = new HashMap<>();

        for (PdoRecord pdo : pdoRecords) {
            OperationKpi kpi = kpiMap.computeIfAbsent(pdo.getOperationId(), id -> {
                OperationKpi ok = new OperationKpi();
                ok.setOperationId(id);
                return ok;
            });
            kpi.setTotalGood(kpi.getTotalGood() + pdo.getGoodQuantity());
            kpi.setTotalScrap(kpi.getTotalScrap() + pdo.getScrapQuantity());
            kpi.setTotalProcessed(kpi.getTotalProcessed() + pdo.getGoodQuantity() + pdo.getScrapQuantity());
        }

        for (PdiRecord pdi : pdiRecords) {
            OperationKpi kpi = kpiMap.get(pdi.getOperationId());
            if (kpi != null) {
                kpi.setTotalDowntimeSeconds(kpi.getTotalDowntimeSeconds() + pdi.getDowntimeSeconds());
            }
        }

        for (AlertRecord alert : alerts) {
            if (alert.getOperationId() != null) {
                OperationKpi kpi = kpiMap.get(alert.getOperationId());
                if (kpi != null) kpi.setTotalAlerts(kpi.getTotalAlerts() + 1);
            }
        }

        for (OperationKpi kpi : kpiMap.values()) {
            if (kpi.getTotalProcessed() > 0) {
                kpi.setAvgQualityRate((double) kpi.getTotalGood() / kpi.getTotalProcessed() * 100);
            }
        }

        return ResponseEntity.ok(new ArrayList<>(kpiMap.values()));
    }

    @GetMapping("/charts/scrap-by-reason")
    public ResponseEntity<Map<String, Integer>> getScrapByReason() {
        List<PdoRecord> pdoRecords = storage.getPdoRecords();
        Map<String, Integer> scrapByReason = new HashMap<>();
        for (PdoRecord pdo : pdoRecords) {
            if (pdo.getScrapReasonCode() != null && !pdo.getScrapReasonCode().isEmpty()) {
                scrapByReason.merge(pdo.getScrapReasonCode(), pdo.getScrapQuantity(), Integer::sum);
            }
        }
        return ResponseEntity.ok(scrapByReason);
    }

    @GetMapping("/orders")
    public ResponseEntity<List<ProductionOrder>> getAllOrders() {
        try {
            return ResponseEntity.ok(storage.loadProductionOrders());
        } catch (IOException e) {
            log.error("Failed to load orders", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    private Map<String, Object> buildSummary(List<ProductionOrder> orders) {
        Map<String, Object> summary = new HashMap<>();
        summary.put("totalProductionOrders", orders.size());
        long totalPanels = orders.stream().mapToInt(ProductionOrder::getPlannedQuantity).sum();
        summary.put("totalPanels", totalPanels);
        long delayed = orders.stream().filter(o -> "DELAYED".equals(o.getStatus())).count();
        summary.put("delayedOrders", delayed);
        return summary;
    }
}