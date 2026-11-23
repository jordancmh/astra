import { GeoJsonLineString } from './route.model';

export type VehicleStatus = 'NOMINAL' | 'INTERSECTED' | 'DISRUPTED' | 'REROUTING' | 'IDLE';

export type CargoPriority = 1 | 2 | 3 | 4 | 5;

export interface FleetVehicle {
  id: string;
  routeId: string;
  latitude: number;
  longitude: number;
  bearing: number;
  speedKmh: number;
  cargoPriority: CargoPriority;
  status: VehicleStatus;
  intersectedAnomalyId: string | null;
  updatedAt: string;
}

export interface FleetRoute {
  id: string;
  vehicleId: string;
  status: string;
  geometry: GeoJsonLineString;
  distanceMeters: number;
  estimatedTimeMs: number;
}

export interface FleetSummary {
  total: number;
  nominal: number;
  rerouting: number;
  disrupted: number;
}

export interface FleetStateResponse {
  summary: FleetSummary;
  vehicles: FleetVehicle[];
  routes: FleetRoute[];
  timestamp: string;
}

export interface SimulationStatus {
  running: boolean;
  vehicleCount: number;
  tickCount: number;
  message: string;
}

export interface FleetEvent {
  id: string;
  vehicleId: string;
  eventType: 'STATUS_CHANGE' | 'ANOMALY_DETECTED' | 'REROUTED' | 'SEEDED';
  previousStatus?: VehicleStatus;
  currentStatus: VehicleStatus;
  message: string;
  timestamp: Date;
}

export const CARGO_PRIORITY_CONFIG: Record<
  CargoPriority,
  { label: string; desc: string; badgeClass: string; color: string }
> = {
  5: {
    label: 'P5 CRITICAL',
    desc: 'Medical / Pharma',
    badgeClass: 'bg-rose-500/20 text-rose-300 border border-rose-500/40',
    color: '#ef4444',
  },
  4: {
    label: 'P4 HIGH',
    desc: 'Cold Chain',
    badgeClass: 'bg-amber-500/20 text-amber-300 border border-amber-500/40',
    color: '#f59e0b',
  },
  3: {
    label: 'P3 MED',
    desc: 'Express Parcels',
    badgeClass: 'bg-blue-500/20 text-blue-300 border border-blue-500/40',
    color: '#3b82f6',
  },
  2: {
    label: 'P2 STD',
    desc: 'Retail Restock',
    badgeClass: 'bg-slate-700/60 text-slate-300 border border-slate-600',
    color: '#94a3b8',
  },
  1: {
    label: 'P1 LOW',
    desc: 'Bulk Cargo',
    badgeClass: 'bg-slate-800 text-slate-400 border border-slate-700',
    color: '#64748b',
  },
};

export const VEHICLE_STATUS_CONFIG: Record<
  VehicleStatus,
  { label: string; color: string; badgeClass: string }
> = {
  NOMINAL: {
    label: 'Nominal',
    color: '#10b981',
    badgeClass: 'bg-emerald-500/20 text-emerald-300 border border-emerald-500/40',
  },
  INTERSECTED: {
    label: 'Intersected',
    color: '#f59e0b',
    badgeClass: 'bg-amber-500/20 text-amber-300 border border-amber-500/40',
  },
  DISRUPTED: {
    label: 'Disrupted',
    color: '#ef4444',
    badgeClass: 'bg-rose-500/20 text-rose-300 border border-rose-500/40',
  },
  REROUTING: {
    label: 'Rerouting',
    color: '#06b6d4',
    badgeClass: 'bg-cyan-500/20 text-cyan-300 border border-cyan-500/40',
  },
  IDLE: {
    label: 'Idle',
    color: '#64748b',
    badgeClass: 'bg-slate-700/40 text-slate-400 border border-slate-600',
  },
};
