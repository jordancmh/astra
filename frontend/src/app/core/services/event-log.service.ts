import { Injectable, inject, signal, computed, DestroyRef } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { timer, of } from 'rxjs';
import { catchError, switchMap, tap } from 'rxjs/operators';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AnomalyService } from './anomaly.service';
import { FleetTelemetryService } from './fleet-telemetry.service';
import {
  BackendNegotiationEvent,
  EventCategory,
  EventSeverity,
  LogEntry,
} from '../models/event-log.model';

@Injectable({
  providedIn: 'root',
})
export class EventLogService {
  private readonly http = inject(HttpClient);
  private readonly anomalyService = inject(AnomalyService);
  private readonly fleetTelemetryService = inject(FleetTelemetryService);
  private readonly destroyRef = inject(DestroyRef);

  private readonly eventsApiUrl = 'http://localhost:8080/api/v1/events?limit=50';
  private readonly eventsFallbackUrl = 'http://localhost:8080/api/events?limit=50';

  // Signals
  readonly events = signal<LogEntry[]>([]);
  readonly categoryFilter = signal<EventCategory>('ALL');
  readonly isDrawerOpen = signal<boolean>(false);

  private readonly seenKeys = new Set<string>();

  // Computed signals
  readonly filteredEvents = computed<LogEntry[]>(() => {
    const filter = this.categoryFilter();
    const list = this.events();
    if (filter === 'ALL') return list;
    return list.filter((e) => e.category === filter);
  });

  readonly latestEvent = computed<LogEntry | null>(() => this.events()[0] ?? null);

  readonly totalCount = computed<number>(() => this.events().length);

  readonly categoryCounts = computed<Record<string, number>>(() => {
    const all = this.events();
    const counts: Record<string, number> = {
      ALL: all.length,
      NEGOTIATION: 0,
      REROUTE: 0,
      ANOMALY: 0,
      TELEMETRY: 0,
      SYSTEM: 0,
    };
    for (const e of all) {
      if (counts[e.category] !== undefined) {
        counts[e.category]++;
      }
    }
    return counts;
  });

  constructor() {
    this.initSubscriptions();
  }

  private initSubscriptions(): void {
    // 1. Polling backend negotiation & reroute audit events (capped at 50)
    timer(0, 2000)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        switchMap(() =>
          this.http.get<BackendNegotiationEvent[]>(this.eventsApiUrl).pipe(
            catchError(() =>
              this.http.get<BackendNegotiationEvent[]>(this.eventsFallbackUrl).pipe(
                catchError(() => of([] as BackendNegotiationEvent[]))
              )
            )
          )
        ),
        tap((backendEvents) => {
          if (!backendEvents || !Array.isArray(backendEvents)) return;
          this.processBackendEvents(backendEvents);
        })
      )
      .subscribe();

    // 2. Local Anomaly Service stream (injection & clear)
    if (this.anomalyService?.anomalyEvents$) {
      this.anomalyService.anomalyEvents$
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe((evt) => {
          const category: LogEntry['category'] =
            evt.eventType === 'INJECTED' ? 'ANOMALY' : 'SYSTEM';
          const severity: EventSeverity =
            evt.severity === 'CRITICAL' || evt.severity === 'HIGH' ? 'HIGH' : 'MEDIUM';

          this.addEntry({
            id: evt.id,
            timestamp: evt.timestamp,
            category,
            message: evt.message,
            severity,
            anomalyId: evt.anomalyId,
            source: 'ANOMALY_SERVICE',
          });
        });
    }

    // 3. Local Fleet Telemetry stream (status transitions & disruptions)
    if (this.fleetTelemetryService?.fleetEvents$) {
      this.fleetTelemetryService.fleetEvents$
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe((evt) => {
          let category: LogEntry['category'] = 'TELEMETRY';
          let severity: EventSeverity = 'LOW';

          if (evt.eventType === 'ANOMALY_DETECTED') {
            category = 'ANOMALY';
            severity = 'HIGH';
          } else if (evt.eventType === 'REROUTED') {
            category = 'REROUTE';
            severity = 'MEDIUM';
          }

          this.addEntry({
            id: evt.id,
            timestamp: evt.timestamp,
            category,
            message: evt.message,
            severity,
            vehicleId: evt.vehicleId,
            source: 'FLEET_SERVICE',
          });
        });
    }
  }

  private processBackendEvents(backendEvents: BackendNegotiationEvent[]): void {
    const newEntries: LogEntry[] = [];

    for (const be of backendEvents) {
      const key = `backend-${be.id}`;
      if (this.seenKeys.has(key)) continue;
      this.seenKeys.add(key);

      const msg = be.eventMessage || '';
      const isNegotiation =
        msg.includes('granted') ||
        msg.includes('yielding') ||
        msg.includes('Right-of-way') ||
        msg.includes('priority');
      const category: LogEntry['category'] = isNegotiation ? 'NEGOTIATION' : 'REROUTE';
      const severity: EventSeverity = isNegotiation ? 'HIGH' : 'MEDIUM';

      newEntries.push({
        id: be.id,
        timestamp: be.createdAt ? new Date(be.createdAt) : new Date(),
        category,
        message: be.eventMessage,
        severity,
        vehicleId: be.vehicleId,
        anomalyId: be.anomalyId,
        affectedRoutes: be.affectedRoutes,
        source: 'BACKEND',
      });
    }

    if (newEntries.length > 0) {
      // Merge and sort newest first, max 100 entries
      const merged = [...newEntries, ...this.events()].sort(
        (a, b) => b.timestamp.getTime() - a.timestamp.getTime()
      );
      this.events.set(merged.slice(0, 100));
    }
  }

  addEntry(entry: LogEntry): void {
    const key = `${entry.source}-${entry.id}`;
    if (this.seenKeys.has(key)) return;
    this.seenKeys.add(key);

    const updated = [entry, ...this.events()].sort(
      (a, b) => b.timestamp.getTime() - a.timestamp.getTime()
    );
    this.events.set(updated.slice(0, 100));
  }

  setCategoryFilter(category: EventCategory): void {
    this.categoryFilter.set(category);
  }

  toggleDrawer(): void {
    this.isDrawerOpen.update((v) => !v);
  }

  setDrawerOpen(isOpen: boolean): void {
    this.isDrawerOpen.set(isOpen);
  }

  clearLocalLog(): void {
    this.events.set([]);
    this.seenKeys.clear();
  }
}
