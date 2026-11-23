import { Component, ChangeDetectionStrategy, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { EventLogService } from '../../core/services/event-log.service';
import { EventCategory } from '../../core/models/event-log.model';

@Component({
  selector: 'app-event-log',
  standalone: true,
  imports: [DatePipe],
  templateUrl: './event-log.component.html',
  styleUrl: './event-log.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EventLogComponent {
  readonly logService = inject(EventLogService);

  readonly filterCategories: { key: EventCategory; label: string }[] = [
    { key: 'ALL', label: 'All' },
    { key: 'NEGOTIATION', label: 'Negotiation' },
    { key: 'REROUTE', label: 'Reroute' },
    { key: 'ANOMALY', label: 'Hazard' },
    { key: 'TELEMETRY', label: 'Telemetry' },
  ];

  onFilterSelect(cat: EventCategory): void {
    this.logService.setCategoryFilter(cat);
  }

  onToggleDrawer(): void {
    this.logService.toggleDrawer();
  }

  onClearLog(event: MouseEvent): void {
    event.stopPropagation();
    this.logService.clearLocalLog();
  }
}
