import { Component, effect, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AnomalyService } from '../../core/services/anomaly.service';
import {
  AnomalyInjectRequest,
  AnomalyPreset,
  AnomalySeverity,
  AnomalyType,
  ANOMALY_TYPE_CONFIG,
  CORRIDOR_PRESETS,
} from '../../core/models/anomaly.model';

@Component({
  selector: 'app-anomaly-action-bar',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './anomaly-action-bar.component.html',
  styleUrls: ['./anomaly-action-bar.component.css'],
})
export class AnomalyActionBarComponent {
  readonly anomalyService = inject(AnomalyService);

  readonly presets = CORRIDOR_PRESETS;
  readonly anomalyTypeConfig = ANOMALY_TYPE_CONFIG;
  readonly anomalyTypes: AnomalyType[] = ['ROAD_CLOSURE', 'CONGESTION', 'WEATHER', 'ACCIDENT'];
  readonly severities: AnomalySeverity[] = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];

  isExpanded = true;
  selectedType: AnomalyType = 'ROAD_CLOSURE';
  selectedSeverity: AnomalySeverity = 'HIGH';
  radiusMeters = 600;
  latitude: number | null = 3.0833;
  longitude: number | null = 101.5833;
  selectedPresetId: string | null = 'preset-fed-hwy';

  isSubmitting = false;
  successMessage: string | null = null;
  errorMessage: string | null = null;

  constructor() {
    // Listen to map click coordinate updates from AnomalyService
    effect(() => {
      const coords = this.anomalyService.selectedCoordinates();
      if (coords) {
        this.longitude = coords[0];
        this.latitude = coords[1];
        this.selectedPresetId = null; // custom location picked
        this.errorMessage = null;
      }
    });
  }

  toggleExpanded(): void {
    this.isExpanded = !this.isExpanded;
  }

  selectType(type: AnomalyType): void {
    this.selectedType = type;
    this.clearMessages();
  }

  selectSeverity(severity: AnomalySeverity): void {
    this.selectedSeverity = severity;
    this.clearMessages();
  }

  applyPreset(preset: AnomalyPreset): void {
    this.selectedPresetId = preset.id;
    this.selectedType = preset.type;
    this.selectedSeverity = preset.severity;
    this.latitude = preset.latitude;
    this.longitude = preset.longitude;
    this.radiusMeters = preset.radiusMeters;
    this.clearMessages();
  }

  toggleDropOnMap(): void {
    const nextState = !this.anomalyService.isDroppingOnMap();
    this.anomalyService.setDroppingOnMap(nextState);
    if (nextState) {
      this.selectedPresetId = null;
    }
  }

  submitInjection(): void {
    this.clearMessages();

    if (this.latitude === null || this.longitude === null) {
      this.errorMessage = 'Please specify coordinates or select a corridor preset.';
      return;
    }

    if (!this.anomalyService.isWithinKlangValley(this.longitude, this.latitude)) {
      this.errorMessage = `Coordinates [${this.longitude.toFixed(4)}, ${this.latitude.toFixed(
        4
      )}] are outside Klang Valley bounds (Lat: 2.90..3.30, Lng: 101.40..101.90).`;
      return;
    }

    if (this.radiusMeters <= 0) {
      this.errorMessage = 'Impact radius must be greater than 0 meters.';
      return;
    }

    const request: AnomalyInjectRequest = {
      anomalyType: this.selectedType,
      severity: this.selectedSeverity,
      latitude: Number(this.latitude),
      longitude: Number(this.longitude),
      radiusMeters: Number(this.radiusMeters),
    };

    this.isSubmitting = true;
    this.anomalyService.injectAnomaly(request).subscribe({
      next: (res) => {
        this.isSubmitting = false;
        this.successMessage = `Disruption ${res.anomalyId} (${res.anomalyType}) injected successfully. ${res.affectedRouteIds.length} route(s) affected.`;
      },
      error: (err) => {
        this.isSubmitting = false;
        this.errorMessage = err?.message || 'Failed to inject network anomaly.';
      },
    });
  }

  clearAll(): void {
    this.clearMessages();
    this.anomalyService.clearAllAnomalies().subscribe({
      next: (res) => {
        this.successMessage = `Network cleared: ${res.clearedCount} disruption(s) resolved.`;
      },
      error: (err) => {
        this.errorMessage = err?.message || 'Failed to clear disruptions.';
      },
    });
  }

  private clearMessages(): void {
    this.successMessage = null;
    this.errorMessage = null;
  }
}
