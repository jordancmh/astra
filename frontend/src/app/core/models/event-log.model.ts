export type EventCategory = 'ALL' | 'NEGOTIATION' | 'REROUTE' | 'ANOMALY' | 'TELEMETRY' | 'SYSTEM';
export type EventSeverity = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';

export interface LogEntry {
  id: string;
  timestamp: Date;
  category: 'NEGOTIATION' | 'REROUTE' | 'ANOMALY' | 'TELEMETRY' | 'SYSTEM';
  message: string;
  severity: EventSeverity;
  vehicleId?: string;
  anomalyId?: string;
  affectedRoutes?: string;
  source: 'BACKEND' | 'ANOMALY_SERVICE' | 'FLEET_SERVICE';
}

export interface BackendNegotiationEvent {
  id: string;
  anomalyId?: string;
  vehicleId?: string;
  affectedRoutes?: string;
  eventMessage: string;
  createdAt: string;
}
