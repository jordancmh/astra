import { Component, ChangeDetectionStrategy, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { MapViewComponent } from '../map/map-view.component';
import { AnomalyActionBarComponent } from '../anomalies/anomaly-action-bar.component';
import { FleetTelemetrySidebarComponent } from '../fleet/fleet-telemetry-sidebar.component';
import { AnomalyService } from '../../core/services/anomaly.service';

@Component({
  selector: 'app-dashboard-shell',
  standalone: true,
  imports: [MapViewComponent, AnomalyActionBarComponent, FleetTelemetrySidebarComponent, DatePipe],
  templateUrl: './dashboard-shell.component.html',
  styleUrl: './dashboard-shell.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DashboardShellComponent {
  readonly anomalyService = inject(AnomalyService);
  readonly currentTime = new Date();
}
