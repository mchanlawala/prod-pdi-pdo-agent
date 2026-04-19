package com.production.agent;

import com.production.domain.AlertRecord;
import com.production.domain.PdiRecord;
import com.production.domain.PdoRecord;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.output.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
public class ExecutionInsightsAgent {

    private final ChatLanguageModel llm;
    private final boolean aiEnabled;

    public ExecutionInsightsAgent(
            @Value("${ollama.base-url:http://localhost:11434}") String baseUrl,
            @Value("${ollama.model:llama3.2}") String modelName,
            @Value("${ollama.temperature:0.2}") double temperature) {
        ChatLanguageModel tmpLlm = null;
        boolean tmpEnabled = false;
        try {
            tmpLlm = OllamaChatModel.builder()
                    .baseUrl(baseUrl)
                    .modelName(modelName)
                    .temperature(temperature)
                    .build();
            tmpEnabled = true;
            log.info("Execution AI agent enabled with Ollama model: {} at {}", modelName, baseUrl);
        } catch (Exception e) {
            log.warn("Failed to initialize Ollama for execution insights: {}", e.getMessage());
        }
        this.llm = tmpLlm;
        this.aiEnabled = tmpEnabled;
    }

    public String analyseExecution(List<PdiRecord> pdiRecords, List<PdoRecord> pdoRecords, List<AlertRecord> alerts) {
        if (!aiEnabled || llm == null) {
            return buildRuleBasedInsights(pdiRecords, pdoRecords, alerts);
        }
        try {
            // Build summary for LLM
            String summary = buildExecutionSummary(pdiRecords, pdoRecords, alerts);
            String prompt = "You are a production execution analyst. Analyse the following PDI/PDO data and alerts, then provide concise, actionable insights and recommendations:\n\n" + summary;
            Response<AiMessage> response = llm.generate(List.of(
                    SystemMessage.from("You are an expert in solar panel manufacturing execution, scrap analysis, and OEE improvement."),
                    UserMessage.from(prompt)
            ));
            return response.content().text();
        } catch (Exception e) {
            log.error("AI analysis failed", e);
            return buildRuleBasedInsights(pdiRecords, pdoRecords, alerts);
        }
    }

    private String buildExecutionSummary(List<PdiRecord> pdiRecords, List<PdoRecord> pdoRecords, List<AlertRecord> alerts) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Production Execution Summary ===\n");
        sb.append("Total operations recorded: ").append(pdiRecords.size()).append("\n");
        sb.append("Total PDO records: ").append(pdoRecords.size()).append("\n");
        sb.append("Total alerts: ").append(alerts.stream().filter(a -> !a.getAcknowledged()).count()).append(" unacknowledged\n\n");

        // Scrap analysis
        Map<String, Double> scrapByReason = pdoRecords.stream()
                .filter(p -> p.getScrapQuantity() > 0)
                .collect(Collectors.groupingBy(
                        PdoRecord::getScrapReasonCode,
                        Collectors.summingDouble(p -> p.getScrapQuantity())
                ));
        sb.append("Scrap by reason:\n");
        scrapByReason.forEach((reason, qty) -> sb.append("  - ").append(reason).append(": ").append(qty).append(" pcs\n"));

        // Cycle time anomalies (from alerts)
        long cycleAlerts = alerts.stream().filter(a -> "CYCLE_TIME_ALERT".equals(a.getType())).count();
        long scrapAlerts = alerts.stream().filter(a -> "SCRAP_ALERT".equals(a.getType())).count();
        sb.append("\nAlerts summary:\n");
        sb.append("  - Cycle time alerts: ").append(cycleAlerts).append("\n");
        sb.append("  - Scrap alerts: ").append(scrapAlerts).append("\n");

        // Top offending operations (by scrap)
        Map<Integer, Integer> scrapByOp = pdoRecords.stream()
                .collect(Collectors.groupingBy(
                        PdoRecord::getOperationId,
                        Collectors.summingInt(PdoRecord::getScrapQuantity)
                ));
        sb.append("\nTop 3 operations by scrap quantity:\n");
        scrapByOp.entrySet().stream()
                .sorted(Map.Entry.<Integer, Integer>comparingByValue().reversed())
                .limit(3)
                .forEach(e -> sb.append("  - Op ").append(e.getKey()).append(": ").append(e.getValue()).append(" scrap\n"));

        return sb.toString();
    }

    private String buildRuleBasedInsights(List<PdiRecord> pdiRecords, List<PdoRecord> pdoRecords, List<AlertRecord> alerts) {
        long scrapAlerts = alerts.stream().filter(a -> "SCRAP_ALERT".equals(a.getType())).count();
        long cycleAlerts = alerts.stream().filter(a -> "CYCLE_TIME_ALERT".equals(a.getType())).count();
        double avgQuality = pdoRecords.stream().mapToDouble(PdoRecord::getQualityPassRate).average().orElse(100.0);
        return String.format("Rule‑based insights: %d operations recorded. Average quality pass rate: %.1f%%. %d scrap alerts, %d cycle time alerts. Review operations with high scrap or cycle time deviations.",
                pdiRecords.size(), avgQuality, scrapAlerts, cycleAlerts);
    }
}