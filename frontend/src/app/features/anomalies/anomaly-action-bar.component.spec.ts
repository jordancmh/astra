import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { AnomalyActionBarComponent } from './anomaly-action-bar.component';
import { AnomalyService } from '../../core/services/anomaly.service';
import { of, throwError } from 'rxjs';
import {
  AnomalyInjectRequest,
  AnomalyInjectResponse,
  CORRIDOR_PRESETS,
  NetworkAnomaly,
} from '../../core/models/anomaly.model';

describe('AnomalyActionBarComponent', () => {
  let component: AnomalyActionBarComponent;
  let fixture: ComponentFixture<AnomalyActionBarComponent>;
  let anomalyServiceSpy: jasmine.SpyObj<AnomalyService>;

  const activeAnomaliesSignal = signal<NetworkAnomaly[]>([]);
  const anomalyCountSignal = signal<number>(0);
  const hasActiveAnomaliesSignal = signal<boolean>(false);
  const isDroppingOnMapSignal = signal<boolean>(false);
  const selectedCoordinatesSignal = signal<[number, number] | null>(null);
  const isLoadingSignal = signal<boolean>(false);

  const mockInjectResponse: AnomalyInjectResponse = {
    anomalyId: 'D-001',
    anomalyType: 'ROAD_CLOSURE',
    severity: 'HIGH',
    affectedRouteIds: ['RT-001'],
    affectedVehicleIds: ['V-001'],
    injectedAt: '2026-10-12T08:34:12Z',
  };

  beforeEach(async () => {
    activeAnomaliesSignal.set([]);
    anomalyCountSignal.set(0);
    hasActiveAnomaliesSignal.set(false);
    isDroppingOnMapSignal.set(false);
    selectedCoordinatesSignal.set(null);
    isLoadingSignal.set(false);

    anomalyServiceSpy = jasmine.createSpyObj(
      'AnomalyService',
      [
        'injectAnomaly',
        'clearAllAnomalies',
        'setDroppingOnMap',
        'setSelectedCoordinates',
        'isWithinKlangValley',
        'fetchActiveAnomalies',
      ]
    );

    (anomalyServiceSpy as any).activeAnomalies = activeAnomaliesSignal;
    (anomalyServiceSpy as any).anomalyCount = anomalyCountSignal;
    (anomalyServiceSpy as any).hasActiveAnomalies = hasActiveAnomaliesSignal;
    (anomalyServiceSpy as any).isDroppingOnMap = isDroppingOnMapSignal;
    (anomalyServiceSpy as any).selectedCoordinates = selectedCoordinatesSignal;
    (anomalyServiceSpy as any).isLoading = isLoadingSignal;

    anomalyServiceSpy.isWithinKlangValley.and.callFake(
      (lng: number, lat: number) => lng >= 101.4 && lng <= 101.9 && lat >= 2.9 && lat <= 3.3
    );
    anomalyServiceSpy.injectAnomaly.and.returnValue(of(mockInjectResponse));
    anomalyServiceSpy.clearAllAnomalies.and.returnValue(of({ clearedCount: 2, timestamp: '' }));
    anomalyServiceSpy.fetchActiveAnomalies.and.returnValue(of([]));

    anomalyServiceSpy.setDroppingOnMap.and.callFake((val: boolean) => {
      isDroppingOnMapSignal.set(val);
    });

    await TestBed.configureTestingModule({
      imports: [AnomalyActionBarComponent],
      providers: [{ provide: AnomalyService, useValue: anomalyServiceSpy }],
    }).compileComponents();

    fixture = TestBed.createComponent(AnomalyActionBarComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create the component with initial defaults', () => {
    expect(component).toBeTruthy();
    expect(component.selectedType).toBe('ROAD_CLOSURE');
    expect(component.selectedSeverity).toBe('HIGH');
    expect(component.radiusMeters).toBe(600);
    expect(component.isExpanded).toBeTrue();
  });

  it('should toggle expanded/collapsed view', () => {
    component.toggleExpanded();
    expect(component.isExpanded).toBeFalse();
    component.toggleExpanded();
    expect(component.isExpanded).toBeTrue();
  });

  it('should select anomaly type and severity', () => {
    component.selectType('CONGESTION');
    expect(component.selectedType).toBe('CONGESTION');

    component.selectSeverity('CRITICAL');
    expect(component.selectedSeverity).toBe('CRITICAL');
  });

  it('should apply corridor preset values when selected', () => {
    const preset = CORRIDOR_PRESETS[0]; // Federal Highway
    component.applyPreset(preset);

    expect(component.selectedPresetId).toBe(preset.id);
    expect(component.selectedType).toBe(preset.type);
    expect(component.selectedSeverity).toBe(preset.severity);
    expect(component.latitude).toBe(preset.latitude);
    expect(component.longitude).toBe(preset.longitude);
    expect(component.radiusMeters).toBe(preset.radiusMeters);
  });

  it('should toggle map drop click-picker mode', () => {
    component.toggleDropOnMap();
    expect(anomalyServiceSpy.setDroppingOnMap).toHaveBeenCalledWith(true);

    // Toggle off
    isDroppingOnMapSignal.set(true);
    component.toggleDropOnMap();
    expect(anomalyServiceSpy.setDroppingOnMap).toHaveBeenCalledWith(false);
  });

  it('should submit injection and handle success', () => {
    component.latitude = 3.1180;
    component.longitude = 101.6210;
    component.radiusMeters = 600;
    component.selectedType = 'ROAD_CLOSURE';
    component.selectedSeverity = 'HIGH';

    component.submitInjection();

    expect(anomalyServiceSpy.injectAnomaly).toHaveBeenCalledWith({
      anomalyType: 'ROAD_CLOSURE',
      severity: 'HIGH',
      latitude: 3.1180,
      longitude: 101.6210,
      radiusMeters: 600,
    });
    expect(component.successMessage).toContain('D-001');
    expect(component.errorMessage).toBeNull();
  });

  it('should display error message if coordinates are missing on submit', () => {
    component.latitude = null;
    component.longitude = null;

    component.submitInjection();

    expect(anomalyServiceSpy.injectAnomaly).not.toHaveBeenCalled();
    expect(component.errorMessage).toContain('Please specify coordinates');
  });

  it('should display error message if backend injection fails', () => {
    component.latitude = 3.1180;
    component.longitude = 101.6210;
    anomalyServiceSpy.injectAnomaly.and.returnValue(
      throwError(() => new Error('Server network failure'))
    );

    component.submitInjection();

    expect(component.errorMessage).toContain('Server network failure');
    expect(component.successMessage).toBeNull();
  });

  it('should invoke clearAllAnomalies when Clear All is clicked', () => {
    component.clearAll();
    expect(anomalyServiceSpy.clearAllAnomalies).toHaveBeenCalled();
  });
});
