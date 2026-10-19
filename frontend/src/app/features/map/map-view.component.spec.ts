import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MapViewComponent } from './map-view.component';
import mapboxgl from 'mapbox-gl';

describe('MapViewComponent', () => {
  let component: MapViewComponent;
  let fixture: ComponentFixture<MapViewComponent>;
  let mockMap: any;

  beforeEach(async () => {
    mockMap = {
      addControl: jasmine.createSpy('addControl'),
      on: jasmine.createSpy('on'),
      remove: jasmine.createSpy('remove'),
    };

    // Spy on mapboxgl constructors to bypass WebGL requirement in headless test runs
    spyOn(mapboxgl, 'Map').and.returnValue(mockMap);
    spyOn(mapboxgl, 'NavigationControl').and.returnValue({} as any);
    spyOn(mapboxgl, 'AttributionControl').and.returnValue({} as any);
    spyOn(mapboxgl, 'ScaleControl').and.returnValue({} as any);

    await TestBed.configureTestingModule({
      imports: [MapViewComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(MapViewComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should initialize mapbox map with dark-v11 style and Klang Valley bounds', () => {
    expect(mapboxgl.Map).toHaveBeenCalledWith(
      jasmine.objectContaining({
        style: 'mapbox://styles/mapbox/dark-v11',
        center: [101.6839, 3.1390],
        zoom: 11,
        maxBounds: [
          [101.40, 2.90],
          [101.90, 3.30],
        ],
      })
    );
  });

  it('should add navigation, attribution, and scale controls', () => {
    expect(mockMap.addControl).toHaveBeenCalledTimes(3);
  });

  it('should remove the map on destroy', () => {
    component.ngOnDestroy();
    expect(mockMap.remove).toHaveBeenCalled();
  });
});
