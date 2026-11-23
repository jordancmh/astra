import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { DashboardShellComponent } from './dashboard-shell.component';
import { RoutingService } from '../../core/services/routing.service';
import { AnomalyService } from '../../core/services/anomaly.service';
import { NetworkAnomaly } from '../../core/models/anomaly.model';
import mapboxgl from 'mapbox-gl';
import { of } from 'rxjs';

describe('DashboardShellComponent', () => {
  let component: DashboardShellComponent;
  let fixture: ComponentFixture<DashboardShellComponent>;
  let anomalyServiceSpy: jasmine.SpyObj<AnomalyService>;

  const activeAnomaliesSignal = signal<NetworkAnomaly[]>([]);
  const anomalyCountSignal = signal<number>(0);
  const hasActiveAnomaliesSignal = signal<boolean>(false);
  const isDroppingOnMapSignal = signal<boolean>(false);
  const selectedCoordinatesSignal = signal<[number, number] | null>(null);
  const isLoadingSignal = signal<boolean>(false);

  beforeEach(async () => {
    activeAnomaliesSignal.set([]);
    anomalyCountSignal.set(0);
    hasActiveAnomaliesSignal.set(false);
    isDroppingOnMapSignal.set(false);
    selectedCoordinatesSignal.set(null);
    isLoadingSignal.set(false);

    // Spy on mapboxgl constructors to bypass WebGL requirement in child MapViewComponent
    spyOn(mapboxgl, 'Map').and.returnValue({
      addControl: jasmine.createSpy('addControl'),
      on: jasmine.createSpy('on'),
      remove: jasmine.createSpy('remove'),
      addSource: jasmine.createSpy('addSource'),
      getSource: jasmine.createSpy('getSource').and.returnValue(undefined),
      addLayer: jasmine.createSpy('addLayer'),
      getLayer: jasmine.createSpy('getLayer').and.returnValue(undefined),
      fitBounds: jasmine.createSpy('fitBounds'),
      getCanvas: jasmine.createSpy('getCanvas').and.returnValue({ style: { cursor: '' } }),
    } as any);
    spyOn(mapboxgl, 'NavigationControl').and.returnValue({} as any);
    spyOn(mapboxgl, 'AttributionControl').and.returnValue({} as any);
    spyOn(mapboxgl, 'ScaleControl').and.returnValue({} as any);

    anomalyServiceSpy = jasmine.createSpyObj('AnomalyService', [
      'injectAnomaly',
      'clearAnomaly',
      'clearAllAnomalies',
      'setDroppingOnMap',
      'setSelectedCoordinates',
      'isWithinKlangValley',
      'fetchActiveAnomalies',
    ]);

    (anomalyServiceSpy as any).activeAnomalies = activeAnomaliesSignal;
    (anomalyServiceSpy as any).anomalyCount = anomalyCountSignal;
    (anomalyServiceSpy as any).hasActiveAnomalies = hasActiveAnomaliesSignal;
    (anomalyServiceSpy as any).isDroppingOnMap = isDroppingOnMapSignal;
    (anomalyServiceSpy as any).selectedCoordinates = selectedCoordinatesSignal;
    (anomalyServiceSpy as any).isLoading = isLoadingSignal;

    anomalyServiceSpy.fetchActiveAnomalies.and.returnValue(of([]));
    anomalyServiceSpy.isWithinKlangValley.and.returnValue(true);

    await TestBed.configureTestingModule({
      imports: [DashboardShellComponent],
      providers: [
        {
          provide: RoutingService,
          useValue: {
            calculateRoute: () =>
              of({
                routeId: 'RT-001',
                status: 'OPTIMAL',
                distanceMeters: 1000,
                estimatedTimeMs: 100000,
                geometry: { type: 'LineString', coordinates: [] },
              }),
          },
        },
        { provide: AnomalyService, useValue: anomalyServiceSpy },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(DashboardShellComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create the dashboard shell', () => {
    expect(component).toBeTruthy();
  });

  it('should render the brand mark ASTRA', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.brand-name')?.textContent).toContain('ASTRA');
  });

  it('should render the system status badge', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.system-status-pill')?.textContent).toContain('SYSTEM OPERATIONAL');
  });

  it('should render region tag KLANG VALLEY', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.region-tag')?.textContent).toContain('KLANG VALLEY');
  });

  it('should render app-anomaly-action-bar within the map viewport', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('app-anomaly-action-bar')).toBeTruthy();
  });

  it('should render active anomalies count and list in sidebar when disruptions exist', () => {
    const mockAnomaly: NetworkAnomaly = {
      id: 'D-001',
      anomalyType: 'ROAD_CLOSURE',
      severity: 'HIGH',
      radiusMeters: 600,
      center: [101.6210, 3.1180],
      polygon: { type: 'Polygon', coordinates: [] },
      injectedAt: '2026-10-12T08:34:12Z',
      isActive: true,
    };

    activeAnomaliesSignal.set([mockAnomaly]);
    anomalyCountSignal.set(1);
    hasActiveAnomaliesSignal.set(true);
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('ROAD_CLOSURE');
    expect(compiled.textContent).toContain('D-001');
  });
});
