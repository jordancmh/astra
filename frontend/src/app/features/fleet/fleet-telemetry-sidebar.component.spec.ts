import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { FleetTelemetrySidebarComponent } from './fleet-telemetry-sidebar.component';
import { FleetTelemetryService } from '../../core/services/fleet-telemetry.service';
import { of } from 'rxjs';
import { signal } from '@angular/core';
import { FleetVehicle, FleetSummary } from '../../core/models/fleet.model';

describe('FleetTelemetrySidebarComponent', () => {
  let component: FleetTelemetrySidebarComponent;
  let fixture: ComponentFixture<FleetTelemetrySidebarComponent>;
  let mockFleetService: jasmine.SpyObj<FleetTelemetryService>;

  const mockVehicles: FleetVehicle[] = [
    {
      id: 'V-001',
      routeId: 'RT-001',
      latitude: 3.0833,
      longitude: 101.5833,
      bearing: 90.0,
      speedKmh: 60.0,
      cargoPriority: 5,
      status: 'DISRUPTED',
      intersectedAnomalyId: 'D-001',
      updatedAt: '2026-11-23T14:30:00Z',
    },
  ];

  const mockSummary: FleetSummary = {
    total: 1,
    nominal: 0,
    rerouting: 0,
    disrupted: 1,
  };

  beforeEach(async () => {
    mockFleetService = jasmine.createSpyObj('FleetTelemetryService', [
      'seedFleet',
      'startSimulation',
      'pauseSimulation',
      'resetSimulation',
      'selectVehicle',
      'setStatusFilter',
    ]);

    // Signals
    (mockFleetService as any).vehicles = signal(mockVehicles);
    (mockFleetService as any).filteredVehicles = signal(mockVehicles);
    (mockFleetService as any).summary = signal(mockSummary);
    (mockFleetService as any).selectedVehicleId = signal<string | null>(null);
    (mockFleetService as any).isSimulationRunning = signal(false);
    (mockFleetService as any).isLoading = signal(false);
    (mockFleetService as any).lastSyncedAt = signal<Date | null>(new Date());
    (mockFleetService as any).statusFilter = signal('ALL');

    await TestBed.configureTestingModule({
      imports: [FleetTelemetrySidebarComponent],
      providers: [
        { provide: FleetTelemetryService, useValue: mockFleetService },
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(FleetTelemetrySidebarComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create and render KPI counts', () => {
    expect(component).toBeTruthy();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Total');
    expect(compiled.textContent).toContain('1'); // total count
    expect(compiled.textContent).toContain('Disrupted');
  });

  it('should render vehicle cards with ID, priority, and bearing', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('V-001');
    expect(compiled.textContent).toContain('P5 CRITICAL');
    expect(compiled.textContent).toContain('90°');
  });

  it('should trigger seedFleet when Seed button is clicked', () => {
    mockFleetService.seedFleet.and.returnValue(of({ running: false, vehicleCount: 5, tickCount: 0, message: 'Seeded' }));
    component.seedFleet();
    expect(mockFleetService.seedFleet).toHaveBeenCalled();
  });

  it('should toggle simulation from paused to running', () => {
    mockFleetService.startSimulation.and.returnValue(of({ running: true, vehicleCount: 5, tickCount: 1, message: 'Running' }));
    component.toggleSimulation();
    expect(mockFleetService.startSimulation).toHaveBeenCalled();
  });

  it('should select vehicle when card is clicked', () => {
    component.selectVehicle('V-001');
    expect(mockFleetService.selectVehicle).toHaveBeenCalledWith('V-001');
  });
});
