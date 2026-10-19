import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DashboardShellComponent } from './dashboard-shell.component';
import mapboxgl from 'mapbox-gl';

describe('DashboardShellComponent', () => {
  let component: DashboardShellComponent;
  let fixture: ComponentFixture<DashboardShellComponent>;

  beforeEach(async () => {
    // Spy on mapboxgl constructors to bypass WebGL requirement in child MapViewComponent
    spyOn(mapboxgl, 'Map').and.returnValue({
      addControl: jasmine.createSpy('addControl'),
      on: jasmine.createSpy('on'),
      remove: jasmine.createSpy('remove'),
    } as any);
    spyOn(mapboxgl, 'NavigationControl').and.returnValue({} as any);
    spyOn(mapboxgl, 'AttributionControl').and.returnValue({} as any);
    spyOn(mapboxgl, 'ScaleControl').and.returnValue({} as any);

    await TestBed.configureTestingModule({
      imports: [DashboardShellComponent],
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
});
