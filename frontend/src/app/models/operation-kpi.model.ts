// src/app/models/operation-kpi.model.ts
export interface OperationKpi {
  operationId: number;
  workCenter?: string;
  description?: string;
  totalProcessed: number;
  totalGood: number;
  totalScrap: number;
  avgQualityRate: number;
  totalDowntimeSeconds: number;
  totalAlerts: number;
}