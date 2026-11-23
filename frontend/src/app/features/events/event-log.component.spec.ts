import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { EventLogComponent } from './event-log.component';
import { EventLogService } from '../../core/services/event-log.service';

describe('EventLogComponent', () => {
  let component: EventLogComponent;
  let fixture: ComponentFixture<EventLogComponent>;
  let logService: EventLogService;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [EventLogComponent],
      providers: [
        EventLogService,
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    logService = TestBed.inject(EventLogService);
    fixture = TestBed.createComponent(EventLogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();

    // Flush initial HTTP polling requests
    const initialReqs = httpMock.match((req) => req.url.includes('/events') || req.url.includes('/fleet') || req.url.includes('/simulation'));
    for (const req of initialReqs) {
      req.flush([]);
    }
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should create the event log component', () => {
    expect(component).toBeTruthy();
  });

  it('should render collapsed state initially', () => {
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.drawer-body')).toBeNull();
    expect(el.querySelector('.ticker-bar')).not.toBeNull();
    expect(el.textContent).toContain('LIVE AUDIT');
  });

  it('should toggle expanded drawer on click', () => {
    const ticker = fixture.nativeElement.querySelector('.ticker-bar') as HTMLElement;
    ticker.click();
    fixture.detectChanges();

    expect(logService.isDrawerOpen()).toBe(true);
    expect(fixture.nativeElement.querySelector('.drawer-body')).not.toBeNull();

    ticker.click();
    fixture.detectChanges();

    expect(logService.isDrawerOpen()).toBe(false);
    expect(fixture.nativeElement.querySelector('.drawer-body')).toBeNull();
  });

  it('should filter events when a filter pill is clicked', () => {
    logService.setDrawerOpen(true);
    fixture.detectChanges();

    logService.addEntry({
      id: 'e-1',
      timestamp: new Date(),
      category: 'NEGOTIATION',
      message: 'V-001 granted Federal Highway priority',
      severity: 'HIGH',
      source: 'BACKEND',
    });
    fixture.detectChanges();

    const pills = fixture.nativeElement.querySelectorAll('.filter-pill') as NodeListOf<HTMLButtonElement>;
    expect(pills.length).toBeGreaterThan(0);

    // Click 'Negotiation' pill (index 1)
    pills[1].click();
    fixture.detectChanges();

    expect(logService.categoryFilter()).toBe('NEGOTIATION');
  });

  it('should clear log entries when clear button is clicked', () => {
    logService.setDrawerOpen(true);
    logService.addEntry({
      id: 'e-test',
      timestamp: new Date(),
      category: 'ANOMALY',
      message: 'Accident reported',
      severity: 'HIGH',
      source: 'ANOMALY_SERVICE',
    });
    fixture.detectChanges();

    expect(logService.totalCount()).toBe(1);

    const clearBtn = fixture.nativeElement.querySelector('.clear-btn') as HTMLButtonElement;
    expect(clearBtn).not.toBeNull();
    clearBtn.click();
    fixture.detectChanges();

    expect(logService.totalCount()).toBe(0);
  });
});
