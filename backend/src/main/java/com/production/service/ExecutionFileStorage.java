package com.production.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.production.domain.*;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class ExecutionFileStorage {
    private static final String DATA_DIR = "./data/";
    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false); // ✅ Ignores unknown fields

    private List<PdiRecord> pdiRecords = new CopyOnWriteArrayList<>();
    private List<PdoRecord> pdoRecords = new CopyOnWriteArrayList<>();
    private List<AlertRecord> alerts = new CopyOnWriteArrayList<>();

    @PostConstruct
    public void init() {
        new File(DATA_DIR).mkdirs();
        pdiRecords = loadList(DATA_DIR + "pdi_records.json", new TypeReference<>() {});
        pdoRecords = loadList(DATA_DIR + "pdo_records.json", new TypeReference<>() {});
        alerts = loadList(DATA_DIR + "alerts.json", new TypeReference<>() {});
    }

    private static class ProductionOrdersWrapper {
        private List<ProductionOrder> productionOrders;
        private Map<String, Object> summary;
        private String aiInsights;
        // getters and setters (or use Lombok @Data)
        public List<ProductionOrder> getProductionOrders() { return productionOrders; }
        public void setProductionOrders(List<ProductionOrder> productionOrders) { this.productionOrders = productionOrders; }
        public Map<String, Object> getSummary() { return summary; }
        public void setSummary(Map<String, Object> summary) { this.summary = summary; }
        public String getAiInsights() { return aiInsights; }
        public void setAiInsights(String aiInsights) { this.aiInsights = aiInsights; }
    }

    private <T> CopyOnWriteArrayList<T> loadList(String path, TypeReference<List<T>> ref) {
        File file = new File(path);
        if (!file.exists() || file.length() == 0) {
            return new CopyOnWriteArrayList<>();
        }
        try {
            List<T> list = mapper.readValue(file, ref);
            return new CopyOnWriteArrayList<>(list);
        } catch (IOException e) {
            System.err.println("Warning: Could not read " + path + " - " + e.getMessage());
            return new CopyOnWriteArrayList<>();
        }
    }

    private <T> void saveToFile(String path, List<T> list) {
        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(new File(path), list);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void addPdi(PdiRecord pdi) {
        pdiRecords.add(pdi);
        saveToFile(DATA_DIR + "pdi_records.json", pdiRecords);
    }

    public void addPdo(PdoRecord pdo) {
        pdoRecords.add(pdo);
        saveToFile(DATA_DIR + "pdo_records.json", pdoRecords);
    }

    public void addAlert(AlertRecord alert) {
        alerts.add(alert);
        saveToFile(DATA_DIR + "alerts.json", alerts);
    }

    public void updateAlert(AlertRecord alert) {
        for (int i = 0; i < alerts.size(); i++) {
            if (alerts.get(i).getId().equals(alert.getId())) {
                alerts.set(i, alert);
                break;
            }
        }
        saveToFile(DATA_DIR + "alerts.json", alerts);
    }

    public List<PdiRecord> getPdiRecords() {
        return new ArrayList<>(pdiRecords);
    }

    public List<PdoRecord> getPdoRecords() {
        return new ArrayList<>(pdoRecords);
    }

    public List<AlertRecord> getAlerts() {
        return new ArrayList<>(alerts);
    }

    public void saveProductionOrders(List<ProductionOrder> orders) throws IOException {
        // Save as plain array (old behaviour)
        mapper.writerWithDefaultPrettyPrinter().writeValue(new File(DATA_DIR + "production_orders.json"), orders);
    }

    public void saveProductionOrdersWithWrapper(List<ProductionOrder> orders, Map<String, Object> summary, String aiInsights) throws IOException {
        ProductionOrdersWrapper wrapper = new ProductionOrdersWrapper();
        wrapper.setProductionOrders(orders);
        wrapper.setSummary(summary);
        wrapper.setAiInsights(aiInsights);
        mapper.writerWithDefaultPrettyPrinter().writeValue(new File(DATA_DIR + "production_orders.json"), wrapper);
    }

    public List<ProductionOrder> loadProductionOrders() throws IOException {
        File file = new File(DATA_DIR + "production_orders.json");
        if (!file.exists() || file.length() == 0) {
            return new ArrayList<>();
        }
        try {
            // Try reading as wrapper (new format)
            ProductionOrdersWrapper wrapper = mapper.readValue(file, ProductionOrdersWrapper.class);
            if (wrapper != null && wrapper.getProductionOrders() != null) {
                return wrapper.getProductionOrders();
            }
        } catch (Exception e) {
            // Fallback: old format (plain array)
            System.err.println("Falling back to plain array format: " + e.getMessage());
            return mapper.readValue(file, new TypeReference<>() {});
        }
        return new ArrayList<>();
    }
}