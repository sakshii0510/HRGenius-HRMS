import { CommonModule } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { AccountPreferencesService } from './account-preferences.service';
import { AuthService } from './auth.service';
import { Employee, EmployeeService } from './employee.service';
import {
  AttendanceRecord,
  HrModulesService,
  JobApplication,
  JobOpening,
  LeaveApplication,
  PayrollRecord,
} from './hr-modules.service';
import { WorkspaceRecord, WorkspaceService } from './workspace.service';
import { NotificationService } from './notification.service';

@Component({
  selector: 'app-employee-home',
  imports: [CommonModule, RouterLink],
  templateUrl: './employee-home.html',
  styleUrl: './employee-home.css',
})
export class EmployeeHomeComponent implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly employeeApi = inject(EmployeeService);
  private readonly hrApi = inject(HrModulesService);
  private readonly workspaceApi = inject(WorkspaceService);
  readonly preferenceStore = inject(AccountPreferencesService);
  readonly notifService = inject(NotificationService);
  readonly user = this.auth.currentUser;
  readonly preferences = this.preferenceStore.preferences;
  readonly employee = signal<Employee | null>(null);
  readonly attendance = signal<AttendanceRecord[]>([]);
  readonly leaves = signal<LeaveApplication[]>([]);
  readonly payroll = signal<PayrollRecord[]>([]);
  readonly openJobs = signal<JobOpening[]>([]);
  readonly applications = signal<JobApplication[]>([]);
  readonly tasks = signal<WorkspaceRecord[]>([]);
  readonly onboarding = signal<WorkspaceRecord[]>([]);
  readonly training = signal<WorkspaceRecord[]>([]);
  readonly announcements = signal<WorkspaceRecord[]>([]);
  readonly loading = signal(true);
  readonly error = signal('');
  readonly pendingLeaveCount = computed(() =>
    this.leaves().filter((leave) => leave.status === 'PENDING').length,
  );
  readonly latestPayroll = computed(() =>
    [...this.payroll()].sort((left, right) =>
      right.salaryPeriod.localeCompare(left.salaryPeriod),
    )[0] ?? null,
  );
  readonly latestAttendance = computed(() =>
    [...this.attendance()].sort((left, right) =>
      right.attendanceDate.localeCompare(left.attendanceDate),
    ).slice(0, 5),
  );
  readonly recentLeaves = computed(() =>
    [...this.leaves()].sort((left, right) =>
      right.appliedAt.localeCompare(left.appliedAt),
    ).slice(0, 5),
  );
  readonly recentApplications = computed(() =>
    [...this.applications()].sort((left, right) =>
      right.appliedAt.localeCompare(left.appliedAt),
    ).slice(0, 5),
  );
  readonly assignedWork = computed(() =>
    [...this.tasks(), ...this.onboarding()]
      .filter((record) => !['COMPLETED', 'ACKNOWLEDGED'].includes(record.status))
      .sort((left, right) =>
        (left.dueDate ?? '9999-12-31').localeCompare(right.dueDate ?? '9999-12-31'),
      )
      .slice(0, 5),
  );

  ngOnInit(): void {
    const email = this.user()?.email;
    if (email) this.preferenceStore.loadFor(email);
    this.notifService.load();
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set('');
    forkJoin({
      employees: this.employeeApi.getAll(),
      attendance: this.hrApi.getAttendance(),
      leaves: this.hrApi.getLeaveApplications(),
      payroll: this.hrApi.getPayroll(),
      jobs: this.hrApi.getJobs('', 'OPEN'),
      applications: this.hrApi.getApplications(),
      tasks: this.workspaceApi.list('TASKS'),
      onboarding: this.workspaceApi.list('ONBOARDING'),
      training: this.workspaceApi.list('TRAINING'),
      announcements: this.workspaceApi.list('ANNOUNCEMENTS'),
    }).subscribe({
      next: (data) => {
        this.employee.set(data.employees[0] ?? null);
        this.attendance.set(data.attendance);
        this.leaves.set(data.leaves);
        this.payroll.set(data.payroll);
        this.openJobs.set(data.jobs);
        this.applications.set(data.applications);
        this.tasks.set(data.tasks);
        this.onboarding.set(data.onboarding);
        this.training.set(data.training);
        this.announcements.set(data.announcements);
        this.loading.set(false);
      },
      error: (error: unknown) => {
        console.error('Unable to load employee dashboard data.', error);
        this.error.set('We couldn’t load your dashboard right now. Please try again in a moment.');
        this.loading.set(false);
      },
    });
  }
}
