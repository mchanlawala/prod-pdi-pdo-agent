package com.production.service;

import com.production.domain.OperationLogRow;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
public class ExcelExecutionParser {
    private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public List<OperationLogRow> parse(MultipartFile file) throws Exception {
        List<OperationLogRow> rows = new ArrayList<>();
        try (InputStream is = file.getInputStream(); Workbook wb = WorkbookFactory.create(is)) {
            Sheet sheet = wb.getSheet("Operation_Log");
            if (sheet == null) throw new RuntimeException("Sheet 'Operation_Log' not found");
            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue; // skip header
                OperationLogRow log = new OperationLogRow();
                log.setProductionOrderId(getCellString(row, 0));
                log.setOperationId((int) getCellNumeric(row, 1));
                log.setActualStart(getDateTimeFromCell(row, 2));
                log.setActualFinish(getDateTimeFromCell(row, 3));
                log.setProcessedQuantity((int) getCellNumeric(row, 4));
                log.setGoodQuantity((int) getCellNumeric(row, 5));
                log.setScrapQuantity((int) getCellNumeric(row, 6));
                log.setScrapReasonCode(getCellString(row, 7));
                log.setDowntimeMinutes((int) getCellNumeric(row, 8));
                log.setOperatorId(getCellString(row, 9));
                log.setRemarks(getCellString(row, 10));
                rows.add(log);
            }
        }
        return rows;
    }

    /**
     * Extracts a LocalDateTime from a cell, handling Excel numeric dates.
     */
    private LocalDateTime getDateTimeFromCell(Row row, int col) {
        Cell cell = row.getCell(col, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) return LocalDateTime.now();

        // Case 1: Numeric cell that is Excel date formatted
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue();
        }

        // Case 2: String cell – parse as yyyy-MM-dd HH:mm
        String s = getCellString(row, col);
        if (s == null || s.trim().isEmpty()) return LocalDateTime.now();
        try {
            return LocalDateTime.parse(s.trim(), DATE_TIME_FMT);
        } catch (Exception e) {
            // Fallback: try parsing as ISO local date time
            try {
                return LocalDateTime.parse(s.trim());
            } catch (Exception ex) {
                System.err.println("Failed to parse date: " + s);
                return LocalDateTime.now();
            }
        }
    }

    private String getCellString(Row row, int col) {
        Cell cell = row.getCell(col, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    // For date cells, return formatted string (optional, used only for logging)
                    yield cell.getLocalDateTimeCellValue().format(DATE_TIME_FMT);
                } else {
                    double val = cell.getNumericCellValue();
                    yield (val == Math.floor(val)) ? String.valueOf((long) val) : String.valueOf(val);
                }
            }
            default -> "";
        };
    }

    private double getCellNumeric(Row row, int col) {
        Cell cell = row.getCell(col, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) return 0;
        return switch (cell.getCellType()) {
            case NUMERIC -> cell.getNumericCellValue();
            case STRING -> {
                try {
                    yield Double.parseDouble(cell.getStringCellValue().trim());
                } catch (NumberFormatException e) {
                    yield 0;
                }
            }
            default -> 0;
        };
    }
}