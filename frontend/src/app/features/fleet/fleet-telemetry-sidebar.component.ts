import { Component, ChangeDetectionStrategy, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FleetTelemetryService } from '../../core/services/fleet-telemetry.service';
import {
  CARGO_PRIORITY_CONFIG,
  VEHICLE_STATUS_CONFIG,
  CargoPriority,
  VehicleStatus,
} from '../../core/models/fleet.model';

@Component({
  selector: 'app-fleet-telemetry-sidebar',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './fleet-telemetry-sidebar.component.html',
  styleUrl: './fleet-telemetry-sidebar.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FleetTelemetrySidebarComponent {
  readonly fleetService = inject(FleetTelemetryService);

  readonly priorityConfig = CARGO_PRIORITY_CONFIG;
  readonly statusConfig = VEHICLE_STATUS_CONFIG;

  seedFleet(): void {
    this.fleetService.seedFleet().subscribe({
      error: (err) => console.error('[Sidebar] Failed to seed fleet:', err),
    });
  }

  toggleSimulation(): void {
    if (this.fleetService.isSimulationRunning()) {
      this.fleetService.pauseSimulation().subscribe();
    } else {
      this.fleetService.startSimulation().subscribe();
    }
  }

  resetSimulation(): void {
    this.fleetService.resetSimulation().subscribe();
  }

  selectVehicle(id: string): void {
    if (this.fleetService.selectedVehicleId() === id) {
      this.fleetService.selectVehicle(null);
    } else {
      this.fleetService.selectVehicle(id);
    }
  }

  setFilter(filter: 'ALL' | 'NOMINAL' | 'DISRUPTED' | 'REROUTING'): void {
    this.fleetService.setStatusFilter(filter);
  }
}
