import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { AuthService } from './auth.service';
import { DashboardComponent } from './dashboard';
import { EmployeeService } from './employee.service';
import { HrModulesService } from './hr-modules.service';
import { WorkspaceService } from './workspace.service';

describe('DashboardComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DashboardComponent],
      providers: [
        provideRouter([]),
        {
          provide: AuthService,
          useValue: {
            currentUser: signal({
              id: 1,
              name: 'Test Admin',
              email: 'admin@example.com',
              role: 'ADMIN',
            }),
            getAccessToken: () => 'test-token',
            isAuthenticated: () => true,
            logout: () => undefined,
          },
        },
        {
          provide: EmployeeService,
          useValue: { getAll: () => of([]) },
        },
        {
          provide: HrModulesService,
          useValue: {
            getAttendance: () => of([]),
            getLeaveApplications: () => of([]),
            getPayroll: () => of([]),
            getJobs: () => of([]),
            getApplications: () => of([]),
          },
        },
        {
          provide: WorkspaceService,
          useValue: {
            dashboardSummary: () =>
              of({
                employeeCount: 0,
                currentPayrollTotal: 0,
                payrollPeriod: '2026-10',
                attendanceTotalToday: 0,
                attendancePresentToday: 0,
                attendanceLateToday: 0,
                attendanceAbsentToday: 0,
                pendingLeaveCount: 0,
                openJobCount: 0,
                openTaskCount: 0,
                publishedAnnouncementCount: 0,
                payrollHistory: [],
              }),
            list: () => of([]),
          },
        },
      ],
    }).compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(DashboardComponent);
    const app = fixture.componentInstance;
    expect(app).toBeTruthy();
  });

  it('should render the HRGenius dashboard', async () => {
    const fixture = TestBed.createComponent(DashboardComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('h1')?.textContent).toContain('Good morning, Test');
  });

  it('should render payroll history as a smooth eight-month area chart', async () => {
    const fixture = TestBed.createComponent(DashboardComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.componentInstance.dashboardSummary.set({
      employeeCount: 1,
      currentPayrollTotal: 800,
      payrollPeriod: '2026-10',
      attendanceTotalToday: 0,
      attendancePresentToday: 0,
      attendanceLateToday: 0,
      attendanceAbsentToday: 0,
      pendingLeaveCount: 0,
      openJobCount: 0,
      openTaskCount: 0,
      publishedAnnouncementCount: 0,
      payrollHistory: [
        ['2026-03', 120],
        ['2026-04', 360],
        ['2026-05', 280],
        ['2026-06', 510],
        ['2026-07', 800],
        ['2026-08', 230],
        ['2026-09', 590],
        ['2026-10', 420],
      ].map(([period, total]) => ({ period: String(period), total: Number(total) })),
    });
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelectorAll('.chart-month-label')).toHaveLength(8);
    expect(compiled.querySelector('.chart-line')?.getAttribute('d')).toContain(' C ');
    expect(compiled.querySelector('.chart-area')?.getAttribute('d')).toMatch(/Z$/);
  });

  it('shows a personal dashboard for employee accounts', async () => {
    const auth = TestBed.inject(AuthService);
    auth.currentUser.set({
      id: 2,
      name: 'Test Employee',
      email: 'employee@example.com',
      role: 'EMPLOYEE',
    });
    const fixture = TestBed.createComponent(DashboardComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.employee-home h1')?.textContent).toContain('Welcome, Test');
    expect(compiled.textContent).toContain('My leave requests');
    expect(compiled.textContent).toContain('My job applications');
  });

  it('calculates employee and payroll status totals from persisted records', async () => {
    const fixture = TestBed.createComponent(DashboardComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    const component = fixture.componentInstance;
    const now = new Date();
    const today = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
    component.employees.set([
      { id: 1, firstName: 'Active', lastName: 'One', email: 'a@example.com', department: null, designation: null, salary: 60000, status: 'ACTIVE' },
      { id: 2, firstName: 'Inactive', lastName: 'Two', email: 'b@example.com', department: null, designation: null, salary: 60000, status: 'INACTIVE' },
      { id: 3, firstName: 'On', lastName: 'Leave', email: 'c@example.com', department: null, designation: null, salary: 60000, status: 'ACTIVE' },
    ]);
    component.dashboardLeaveApplications.set([
      {
        id: 5,
        employeeId: 3,
        employeeName: 'On Leave',
        leaveType: 'ANNUAL',
        startDate: today,
        endDate: today,
        reason: 'Personal',
        status: 'APPROVED',
        appliedAt: today,
        reviewedAt: today,
        reviewComment: null,
      },
    ]);
    component.dashboardPayrollRecords.set([
      { id: 1, employeeId: 1, employeeName: 'Active One', salaryPeriod: today.slice(0, 7), basicSalary: 100, allowances: 0, deductions: 0, netSalary: 100, paymentStatus: 'PAID' },
      { id: 2, employeeId: 2, employeeName: 'Inactive Two', salaryPeriod: today.slice(0, 7), basicSalary: 50, allowances: 0, deductions: 0, netSalary: 50, paymentStatus: 'PENDING' },
    ]);

    expect(component.dashboardEmployeeStatusCounts()).toEqual({
      active: 1,
      inactive: 1,
      onLeave: 1,
    });
    expect(component.payrollStatusAmount('PAID')).toBe(100);
    expect(component.payrollStatusAmount('PENDING')).toBe(50);
  });
});
