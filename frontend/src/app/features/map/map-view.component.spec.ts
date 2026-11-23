import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { MapViewComponent } from './map-view.component';
import { AnomalyService } from '../../core/services/anomaly.service';
import { FleetTelemetryService } from '../../core/services/fleet-telemetry.service';
import { NetworkAnomaly } from '../../core/models/anomaly.model';
import { FleetRoute, FleetVehicle } from '../../core/models/fleet.model';
import mapboxgl from 'mapbox-gl';
import { of } from 'rxjs';

describe('MapViewComponent', () => {
  let component: MapViewComponent;
  let fixture: ComponentFixture<MapViewComponent>;
  let mockMap: any;
  let mockCanvas: any;
  let anomalyServiceSpy: jasmine.SpyObj<AnomalyService>;
  let fleetServiceSpy: jasmine.SpyObj<FleetTelemetryService>;
  let registeredMapEventHandlers: { [key: string]: Function } = {};

  const activeAnomaliesSignal = signal<NetworkAnomaly[]>([]);
  const isDroppingOnMapSignal = signal<boolean>(false);
  const routesSignal = signal<FleetRoute[]>([]);
  const vehiclesSignal = signal<FleetVehicle[]>([]);
  const selectedVehicleIdSignal = signal<string | null>(null);

  const mockAnomaly: NetworkAnomaly = {
    id: 'D-001',
    anomalyType: 'ROAD_CLOSURE',
    severity: 'HIGH',
    radiusMeters: 600,
    center: [101.6210, 3.1180],
    polygon: {
      type: 'Polygon',
      coordinates: [
        [
          [101.615, 3.112],
          [101.627, 3.112],
          [101.627, 3.124],
          [101.615, 3.124],
          [101.615, 3.112],
        ],
      ],
    },
    injectedAt: '2026-10-12T08:34:12Z',
    isActive: true,
  };

  const mockVehicle: FleetVehicle = {
    id: 'V-001',
    routeId: 'RT-001',
    latitude: 3.0833,
    longitude: 101.5833,
    bearing: 90.0,
    speedKmh: 60.0,
    cargoPriority: 5,
    status: 'NOMINAL',
    intersectedAnomalyId: null,
    updatedAt: '2026-11-23T14:30:00Z',
  };

  const mockRoute: FleetRoute = {
    id: 'RT-001',
    vehicleId: 'V-001',
    status: 'OPTIMAL',
    geometry: {
      type: 'LineString',
      coordinates: [
        [101.58, 3.08],
        [101.60, 3.10],
      ],
    },
    distanceMeters: 5000,
    estimatedTimeMs: 300000,
  };

  beforeEach(async () => {
    registeredMapEventHandlers = {};
    mockCanvas = { style: { cursor: '' } };
    activeAnomaliesSignal.set([]);
    isDroppingOnMapSignal.set(false);
    routesSignal.set([]);
    vehiclesSignal.set([]);
    selectedVehicleIdSignal.set(null);

    mockMap = {
      addControl: jasmine.createSpy('addControl'),
      on: jasmine.createSpy('on').and.callFake((...args: any[]) => {
        if (args.length === 2) {
          const [event, cb] = args;
          registeredMapEventHandlers[event] = cb;
          if (event === 'load') {
            cb();
          }
        } else if (args.length === 3) {
          const [event, layer, cb] = args;
          registeredMapEventHandlers[`${event}:${layer}`] = cb;
        }
      }),
      remove: jasmine.createSpy('remove'),
      addSource: jasmine.createSpy('addSource'),
      getSource: jasmine.createSpy('getSource').and.returnValue(undefined),
      addLayer: jasmine.createSpy('addLayer'),
      getLayer: jasmine.createSpy('getLayer').and.returnValue(undefined),
      fitBounds: jasmine.createSpy('fitBounds'),
      flyTo: jasmine.createSpy('flyTo'),
      getCanvas: jasmine.createSpy('getCanvas').and.returnValue(mockCanvas),
      hasImage: jasmine.createSpy('hasImage').and.returnValue(false),
      addImage: jasmine.createSpy('addImage'),
    };

    spyOn(mapboxgl, 'Map').and.returnValue(mockMap);
    spyOn(mapboxgl, 'NavigationControl').and.returnValue({} as any);
    spyOn(mapboxgl, 'AttributionControl').and.returnValue({} as any);
    spyOn(mapboxgl, 'ScaleControl').and.returnValue({} as any);

    anomalyServiceSpy = jasmine.createSpyObj('AnomalyService', [
      'fetchActiveAnomalies',
      'clearAnomaly',
      'setDroppingOnMap',
      'setSelectedCoordinates',
      'isWithinKlangValley',
    ]);
    (anomalyServiceSpy as any).activeAnomalies = activeAnomaliesSignal;
    (anomalyServiceSpy as any).isDroppingOnMap = isDroppingOnMapSignal;

    anomalyServiceSpy.fetchActiveAnomalies.and.returnValue(of([]));
    anomalyServiceSpy.clearAnomaly.and.returnValue(of(undefined));
    anomalyServiceSpy.isWithinKlangValley.and.callFake(
      (lng: number, lat: number) => lng >= 101.4 && lng <= 101.9 && lat >= 2.9 && lat <= 3.3
    );

    fleetServiceSpy = jasmine.createSpyObj('FleetTelemetryService', [
      'fetchFleetState',
      'selectVehicle',
    ]);
    (fleetServiceSpy as any).routes = routesSignal;
    (fleetServiceSpy as any).vehicles = vehiclesSignal;
    (fleetServiceSpy as any).selectedVehicleId = selectedVehicleIdSignal;
    fleetServiceSpy.fetchFleetState.and.returnValue(of(null));

    await TestBed.configureTestingModule({
      imports: [MapViewComponent],
      providers: [
        { provide: AnomalyService, useValue: anomalyServiceSpy },
        { provide: FleetTelemetryService, useValue: fleetServiceSpy },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(MapViewComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should add anomaly GeoJSON source and fill/line layers on load', () => {
    expect(mockMap.addSource).toHaveBeenCalledWith(
      'anomalies-source',
      jasmine.objectContaining({ type: 'geojson' })
    );

    expect(mockMap.addLayer).toHaveBeenCalledWith(
      jasmine.objectContaining({
        id: 'anomalies-fill',
        type: 'fill',
        source: 'anomalies-source',
      })
    );

    expect(anomalyServiceSpy.fetchActiveAnomalies).toHaveBeenCalled();
  });

  it('should add fleet route and vehicle GeoJSON layers on load', () => {
    expect(mockMap.addSource).toHaveBeenCalledWith(
      'fleet-routes-source',
      jasmine.objectContaining({ type: 'geojson' })
    );

    expect(mockMap.addSource).toHaveBeenCalledWith(
      'fleet-vehicles-source',
      jasmine.objectContaining({ type: 'geojson' })
    );

    expect(mockMap.addLayer).toHaveBeenCalledWith(
      jasmine.objectContaining({
        id: 'fleet-routes-line',
        type: 'line',
        source: 'fleet-routes-source',
      })
    );

    expect(mockMap.addLayer).toHaveBeenCalledWith(
      jasmine.objectContaining({
        id: 'fleet-vehicles-circle',
        type: 'circle',
        source: 'fleet-vehicles-source',
      })
    );

    expect(fleetServiceSpy.fetchFleetState).toHaveBeenCalled();
  });

  it('should update fleet routes source when routes signal updates', () => {
    const mockRouteSource = { setData: jasmine.createSpy('setData') };
    mockMap.getSource.and.callFake((id: string) => {
      if (id === 'fleet-routes-source') return mockRouteSource;
      return undefined;
    });

    routesSignal.set([mockRoute]);
    fixture.detectChanges();

    expect(mockRouteSource.setData).toHaveBeenCalledWith(
      jasmine.objectContaining({
        type: 'FeatureCollection',
        features: jasmine.arrayContaining([
          jasmine.objectContaining({
            id: 'RT-001',
            type: 'Feature',
            geometry: mockRoute.geometry,
          }),
        ]),
      })
    );
  });

  it('should update fleet vehicles source when vehicles signal updates', () => {
    const mockVehicleSource = { setData: jasmine.createSpy('setData') };
    mockMap.getSource.and.callFake((id: string) => {
      if (id === 'fleet-vehicles-source') return mockVehicleSource;
      return undefined;
    });

    vehiclesSignal.set([mockVehicle]);
    fixture.detectChanges();

    expect(mockVehicleSource.setData).toHaveBeenCalledWith(
      jasmine.objectContaining({
        type: 'FeatureCollection',
        features: jasmine.arrayContaining([
          jasmine.objectContaining({
            id: 'V-001',
            type: 'Feature',
            geometry: {
              type: 'Point',
              coordinates: [101.5833, 3.0833],
            },
          }),
        ]),
      })
    );
  });

  it('should fly to vehicle when selectedVehicleId changes', () => {
    vehiclesSignal.set([mockVehicle]);
    selectedVehicleIdSignal.set('V-001');
    fixture.detectChanges();

    expect(mockMap.flyTo).toHaveBeenCalledWith(
      jasmine.objectContaining({
        center: [101.5833, 3.0833],
        zoom: 13.5,
      })
    );
  });

  it('should remove the map and current popup on destroy', () => {
    component.ngOnDestroy();
    expect(mockMap.remove).toHaveBeenCalled();
  });
});
