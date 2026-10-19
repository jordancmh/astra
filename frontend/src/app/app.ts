import { Component } from '@angular/core';
import { DashboardShellComponent } from './features/dashboard/dashboard-shell.component';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [DashboardShellComponent],
  template: `<app-dashboard-shell></app-dashboard-shell>`,
  styles: [`:host { display: block; height: 100dvh; width: 100vw; }`],
})
export class App {}
export const AppComponent = App;
