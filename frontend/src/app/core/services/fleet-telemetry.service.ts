import { Injectable, inject, signal, computed, DestroyRef } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Subject, Observable, timer, catchError, of, tap, switchMap } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  FleetVehicle,
  FleetRoute,
  FleetSummary,
  FleetStateResponse,
  SimulationStatus,
  FleetEvent,
  VehicleStatus,
} from '../models/fleet.model';

@Injectable({
  providedIn: 'root',
})
export class FleetTelemetryService {
  private readonly http = inject(HttpClient);
  private readonly destroyRef = inject(DestroyRef);

  private readonly fleetApiUrl = 'http://localhost:8080/api/v1/fleet';
  private readonly simulationApiUrl = 'http://localhost:8080/api/v1/simulation';

  // State Signals
  readonly vehicles = signal<FleetVehicle[]>([]);
  readonly routes = signal<FleetRoute[]>([]);
  readonly summary = signal<FleetSummary>({ total: 0, nominal: 0, rerouting: 0, disrupted: 0 });
  readonly selectedVehicleId = signal<string | null>(null);
  readonly isSimulationRunning = signal<boolean>(false);
  readonly isLoading = signal<boolean>(false);
  readonly lastSyncedAt = signal<Date | null>(null);
  readonly statusFilter = signal<'ALL' | 'NOMINAL' | 'DISRUPTED' | 'REROUTING'>('ALL');

  // Computed Signals
  readonly selectedVehicle = computed<FleetVehicle | null>(() => {
    const id = this.selectedVehicleId();
    if (!id) return null;
    return this.vehicles().find((v) => v.id === id) ?? null;
  });

  readonly filteredVehicles = computed<FleetVehicle[]>(() => {
    const filter = this.statusFilter();
    const all = this.vehicles();
    if (filter === 'ALL') return all;
    return all.filter((v) => v.status === filter);
  });

  // Event stream for Event Log (Issue #20 integration)
  readonly fleetEvents$ = new Subject<FleetEvent>();

  // Status transition tracking
  private readonly lastKnownStatuses = new Map<string, VehicleStatus>();
  private isPollingActive = true;

  constructor() {
    this.initPolling();
    this.checkSimulationStatus();
  }

  private initPolling(): void {
    timer(0, 2000)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        switchMap(() => {
          if (!this.isPollingActive) return of(null);
          return this.fetchFleetState();
        })
      )
      .subscribe();
  }

  fetchFleetState(): Observable<FleetStateResponse | null> {
    return this.http.get<FleetStateResponse>(this.fleetApiUrl).pipe(
      tap((response) => {
        if (!response) return;
        this.updateFleetState(response);
      }),
      catchError((err) => {
        // Fallback convenience alias if needed
        return this.http.get<FleetStateResponse>('http://localhost:8080/api/fleet').pipe(
          tap((fallbackResponse) => {
            if (fallbackResponse) this.updateFleetState(fallbackResponse);
          }),
          catchError(() => {
            console.debug('[FleetTelemetryService] Backend offline or waiting for seeding.');
            return of(null);
          })
        );
      })
    );
  }

  private updateFleetState(response: FleetStateResponse): void {
    const prevVehicles = this.vehicles();
    this.vehicles.set(response.vehicles ?? []);
    this.routes.set(response.routes ?? []);
    this.summary.set(response.summary ?? { total: 0, nominal: 0, rerouting: 0, disrupted: 0 });
    this.lastSyncedAt.set(new Date());

    // Detect status transitions & emit events
    for (const v of response.vehicles ?? []) {
      const prev = this.lastKnownStatuses.get(v.id);
      if (prev && prev !== v.status) {
        let eventType: FleetEvent['eventType'] = 'STATUS_CHANGE';
        let msg = `${v.id} transitioned from ${prev} to ${v.status}`;

        if (v.status === 'DISRUPTED') {
          eventType = 'ANOMALY_DETECTED';
          msg = `${v.id} detected route disruption (${v.intersectedAnomalyId ?? 'Hazard Zone'})`;
        } else if (v.status === 'REROUTING') {
          eventType = 'REROUTED';
          msg = `${v.id} accepted autonomous detour recalculation`;
        }

        this.fleetEvents$.next({
          id: `fe-${Date.now()}-${v.id}`,
          vehicleId: v.id,
          eventType,
          previousStatus: prev,
          currentStatus: v.status,
          message: msg,
          timestamp: new Date(),
        });
      }
      this.lastKnownStatuses.set(v.id, v.status);
    }
  }

  checkSimulationStatus(): void {
    this.http
      .get<SimulationStatus>(`${this.simulationApiUrl}/status`)
      .pipe(
        catchError(() => of({ running: false, vehicleCount: 0, tickCount: 0, message: 'Offline' }))
      )
      .subscribe((status) => {
        if (status) {
          this.isSimulationRunning.set(!!status.running);
        }
      });
  }

  seedFleet(): Observable<SimulationStatus> {
    this.isLoading.set(true);
    return this.http.post<SimulationStatus>(`${this.simulationApiUrl}/seed`, {}).pipe(
      tap((res) => {
        this.isLoading.set(false);
        this.fetchFleetState().subscribe();
        this.fleetEvents$.next({
          id: `seed-${Date.now()}`,
          vehicleId: 'SYSTEM',
          eventType: 'SEEDED',
          currentStatus: 'NOMINAL',
          message: `Seeded ${res.vehicleCount} corridor vehicles in the Klang Valley`,
          timestamp: new Date(),
        });
      }),
      catchError((err) => {
        this.isLoading.set(false);
        throw err;
      })
    );
  }

  startSimulation(): Observable<SimulationStatus> {
    return this.http.post<SimulationStatus>(`${this.simulationApiUrl}/start`, {}).pipe(
      tap((res) => {
        this.isSimulationRunning.set(res.running);
      })
    );
  }

  pauseSimulation(): Observable<SimulationStatus> {
    return this.http.post<SimulationStatus>(`${this.simulationApiUrl}/stop`, {}).pipe(
      tap((res) => {
        this.isSimulationRunning.set(res.running);
      })
    );
  }

  resetSimulation(): Observable<SimulationStatus> {
    this.isLoading.set(true);
    return this.http.delete<SimulationStatus>(`${this.simulationApiUrl}/reset`).pipe(
      tap(() => {
        this.isLoading.set(false);
        this.isSimulationRunning.set(false);
        this.selectedVehicleId.set(null);
        this.vehicles.set([]);
        this.routes.set([]);
        this.summary.set({ total: 0, nominal: 0, rerouting: 0, disrupted: 0 });
        this.lastKnownStatuses.clear();
      }),
      catchError((err) => {
        this.isLoading.set(false);
        throw err;
      })
    );
  }

  selectVehicle(vehicleId: string | null): void {
    this.selectedVehicleId.set(vehicleId);
  }

  setStatusFilter(filter: 'ALL' | 'NOMINAL' | 'DISRUPTED' | 'REROUTING'): void {
    this.statusFilter.set(filter);
  }

  setPollingActive(active: boolean): void {
    this.isPollingActive = active;
  }
}
