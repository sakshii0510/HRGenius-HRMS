import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule, NgForm } from '@angular/forms';
import { NavigationEnd, Router, RouterLink } from '@angular/router';
import { filter, map, Observable } from 'rxjs';
import { AuthService } from './auth.service';
import { Employee, EmployeePayload, EmployeeService } from './employee.service';
import { HrModulesComponent, HrPage } from './hr-modules';
import { WorkspaceComponent, WorkspacePage } from './workspace';
import { DashboardSummary, WorkspaceModule, WorkspaceRecord, WorkspaceService } from './workspace.service';
import { HrModulesService, LeaveApplication, PayrollRecord } from './hr-modules.service';
import { DEPARTMENT_OPTIONS } from './department-options';
import { EmployeeHomeComponent } from './employee-home';
import { AccountSettingsComponent } from './account-settings';
import { NotificationService } from './notification.service';

type PageKey =
  | 'dashboard'
  | 'employees'
  | 'onboarding'
  | 'performance'
  | 'recruitment'
  | 'training'
  | 'attendance'
  | 'leave'
  | 'payroll'
  | 'documents'
  | 'announcements'
  | 'analytics'
  | 'tasks'
  | 'settings';
type AuthPage = 'login' | 'signup' | 'forgot';
type EmployeeForm = EmployeePayload;
interface PayrollChartPoint {
  period: string;
  x: number;
  y: number;
}

@Component({
  selector: 'app-dashboard',
  imports: [
    CommonModule,
    FormsModule,
    RouterLink,
    HrModulesComponent,
    WorkspaceComponent,
    EmployeeHomeComponent,
    AccountSettingsComponent,
  ],
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class DashboardComponent implements OnInit {
  private readonly employeeService = inject(EmployeeService);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly workspaceService = inject(WorkspaceService);
  private readonly hrModulesService = inject(HrModulesService);
  readonly notifService = inject(NotificationService);
  private employeeLoadedRoute: string | null = null;
  private dashboardLoaded = false;
  private notifLoaded = false;

  readonly activePage = signal<PageKey>('dashboard');
  readonly activeHrPage = computed<HrPage>(() => {
    const page = this.activePage();
    return page === 'attendance' || page === 'leave' || page === 'payroll' || page === 'recruitment'
      ? page
      : 'attendance';
  });
  readonly activeWorkspacePage = computed<WorkspacePage>(() => {
    const page = this.activePage();
    return page === 'onboarding' || page === 'performance' || page === 'training' ||
      page === 'documents' || page === 'announcements' || page === 'analytics' ||
      page === 'tasks' ? page : 'onboarding';
  });
  readonly authPage = signal<AuthPage | null>(null);
  readonly authNotice = signal('');
  readonly authError = signal('');
  readonly authBusy = signal(false);
  readonly currentUser = this.authService.currentUser;
  readonly isAdmin = computed(() => this.currentUser()?.role === 'ADMIN');
  readonly authForm = { name: '', email: '', password: '' };
  readonly employees = signal<Employee[]>([]);
  readonly loading = signal(false);
  readonly saving = signal(false);
  readonly pageError = signal('');
  readonly employeeSuccess = signal('');
  readonly dashboardSummary = signal<DashboardSummary | null>(null);
  readonly dashboardPayrollRecords = signal<PayrollRecord[]>([]);
  readonly dashboardLeaveApplications = signal<LeaveApplication[]>([]);
  readonly dashboardTasks = signal<WorkspaceRecord[]>([]);
  readonly dashboardAnnouncements = signal<WorkspaceRecord[]>([]);
  readonly dashboardLoading = signal(false);
  readonly dashboardError = signal('');
  readonly formError = signal('');
  readonly searchTerm = signal('');
  readonly showEmployeeDialog = signal(false);
  readonly selectedEmployee = signal<Employee | null>(null);
  readonly editingEmployee = signal<Employee | null>(null);
  readonly form: EmployeeForm = this.emptyForm();
  readonly departmentOptions = computed(() => [
    ...new Set([
      ...DEPARTMENT_OPTIONS,
      ...this.employees()
        .map((employee) => employee.department?.trim())
        .filter((department): department is string => Boolean(department)),
    ]),
  ]);
  readonly dashboardPayrollPayments = computed(() =>
    this.dashboardPayrollRecords().reduce(
      (totals, record) => record.paymentStatus === 'PAID'
        ? { paid: totals.paid + record.netSalary, pending: totals.pending }
        : { paid: totals.paid, pending: totals.pending + record.netSalary },
      { paid: 0, pending: 0 },
    ),
  );
  readonly dashboardEmployeeStatusCounts = computed(() => {
    const today = this.today();
    const activeIdsOnLeave = new Set(
      this.dashboardLeaveApplications()
        .filter((leave) =>
          leave.status === 'APPROVED' &&
          leave.startDate <= today &&
          leave.endDate >= today,
        )
        .map((leave) => leave.employeeId),
    );
    const inactive = this.employees().filter((employee) => employee.status === 'INACTIVE').length;
    const onLeave = this.employees().filter((employee) =>
      employee.status === 'ACTIVE' && activeIdsOnLeave.has(employee.id),
    ).length;
    return { active: Math.max(0, this.employees().length - inactive - onLeave), inactive, onLeave };
  });
  readonly dashboardPayrollPaymentTotal = computed(() => {
    const totals = this.dashboardPayrollPayments();
    return totals.paid + totals.pending;
  });

  readonly filteredEmployees = computed(() => {
    const term = this.searchTerm().trim().toLowerCase();
    if (!term) return this.employees();
    return this.employees().filter((employee) =>
      [
        employee.firstName,
        employee.lastName,
        employee.email,
        employee.department,
        employee.designation,
      ]
        .filter(Boolean)
        .some((value) => value!.toLowerCase().includes(term)),
    );
  });

  readonly pageTitles: Record<PageKey, string> = {
    dashboard: 'Dashboard',
    employees: 'Employees',
    onboarding: 'Onboarding',
    performance: 'Performance',
    recruitment: 'Recruitment',
    training: 'Training & Development',
    attendance: 'Attendance',
    leave: 'Leave Management',
    payroll: 'Payroll',
    documents: 'Documents',
    announcements: 'Announcements',
    analytics: 'Reports & Analytics',
    tasks: 'Task Management',
    settings: 'Account Settings',
  };

  constructor() {
    this.router.events
      .pipe(
        filter((event) => event instanceof NavigationEnd),
        takeUntilDestroyed(),
      )
      .subscribe(() => this.syncRoute());
  }

  ngOnInit(): void {
    this.syncRoute();
  }

  loadEmployees(): void {
    this.loading.set(true);
    this.pageError.set('');
    this.employeeSuccess.set('');
    this.employeeService.getAll().subscribe({
      next: (employees) => {
        this.employees.set(employees);
        this.loading.set(false);
      },
      error: (error: unknown) => {
        console.error('Unable to load employees from the HR API.', error);
        this.pageError.set(this.apiErrorMessage(error, 'We couldn’t load employees right now. Please try again.'));
        this.loading.set(false);
      },
    });
  }

  loadDashboardData(): void {
    this.dashboardLoading.set(true);
    this.dashboardError.set('');
    this.workspaceService.dashboardSummary().subscribe({
      next: (summary) => this.dashboardSummary.set(summary),
      error: (error: unknown) => {
        console.error('Unable to load live dashboard metrics.', error);
        this.dashboardError.set(this.apiErrorMessage(error, 'Dashboard metrics could not be loaded.'));
      },
    });
    const period = this.currentPayrollPeriod();
    this.hrModulesService.getPayroll({ fromPeriod: period, toPeriod: period }).subscribe({
      next: (records) => this.dashboardPayrollRecords.set(records),
      error: (error: unknown) => {
        console.error('Unable to load current payroll details.', error);
        this.dashboardError.set(this.apiErrorMessage(error, 'Payroll details could not be loaded.'));
      },
    });
    this.hrModulesService.getLeaveApplications().subscribe({
      next: (leaves) => this.dashboardLeaveApplications.set(leaves),
      error: (error: unknown) => {
        console.error('Unable to load leave status for the employee dashboard.', error);
        this.dashboardError.set(this.apiErrorMessage(error, 'Leave data could not be loaded.'));
      },
    });
    this.workspaceService.list('TASKS').subscribe({
      next: (records) => {
        this.dashboardTasks.set(
          records.filter((record) => record.status !== 'COMPLETED').slice(0, 3),
        );
        this.dashboardLoading.set(false);
      },
      error: (error: unknown) => {
        console.error('Unable to load dashboard tasks.', error);
        this.dashboardError.set(this.apiErrorMessage(error, 'Dashboard tasks could not be loaded.'));
        this.dashboardLoading.set(false);
      },
    });
    this.workspaceService.list('ANNOUNCEMENTS').subscribe({
      next: (records) => this.dashboardAnnouncements.set(records.slice(0, 3)),
      error: (error: unknown) => {
        console.error('Unable to load dashboard announcements.', error);
        this.dashboardError.set(this.apiErrorMessage(error, 'Announcements could not be loaded.'));
      },
    });
  }

  completeDashboardTask(task: WorkspaceRecord): void {
    this.workspaceService.updateStatus('TASKS', task.id, 'COMPLETED').subscribe({
      next: (updated) => {
        this.dashboardTasks.update((tasks) =>
          tasks.map((item) => item.id === updated.id ? updated : item),
        );
        this.dashboardSummary.update((summary) => summary
          ? { ...summary, openTaskCount: Math.max(0, summary.openTaskCount - 1) }
          : summary);
      },
      error: (error: unknown) => {
        console.error('Unable to complete dashboard task.', error);
        this.dashboardError.set(this.apiErrorMessage(error, 'Task status could not be updated.'));
      },
    });
  }

  payrollChartTicks(): number[] {
    const maximum = Math.max(...(this.dashboardSummary()?.payrollHistory.map((month) => month.total) ?? []), 0);
    const chartMaximum = Math.max(800, Math.ceil(maximum / 200) * 200);
    return [4, 3, 2, 1, 0].map((step) => chartMaximum * step / 4);
  }

  payrollChartY(value: number): number {
    const chartMaximum = this.payrollChartTicks()[0] || 800;
    return 12 + (1 - value / chartMaximum) * 160;
  }

  payrollAxisLabel(value: number): string {
    if (value >= 1_000_000) return `$${Math.round(value / 100_000) / 10}M`;
    if (value >= 1_000) return `$${Math.round(value / 100) / 10}K`;
    return `$${Math.round(value)}`;
  }

  attendanceSegmentWidth(value: number): number {
    const total = this.employees().length;
    return total > 0 ? value / total * 100 : 0;
  }

  payrollSegmentWidth(value: number): number {
    const total = this.dashboardPayrollPaymentTotal();
    return total > 0 ? value / total * 100 : 0;
  }

  payrollStatusAmount(status: 'PAID' | 'PENDING'): number {
    return this.dashboardPayrollPayments()[status === 'PAID' ? 'paid' : 'pending'];
  }

  private today(): string {
    const now = new Date();
    return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
  }

  payrollChartPoints(): PayrollChartPoint[] {
    const history = this.dashboardSummary()?.payrollHistory ?? [];
    if (history.length === 0) return [];
    const left = 64;
    const width = 566;
    return history.map((month, index) => ({
      period: month.period,
      x: history.length === 1 ? left + width / 2 : left + index * width / (history.length - 1),
      y: this.payrollChartY(month.total),
    }));
  }

  payrollCurvePath(): string {
    const points = this.payrollChartPoints();
    if (points.length === 0) return '';
    if (points.length === 1) return `M ${points[0].x} ${points[0].y}`;
    return points.reduce((path, point, index) => {
      if (index === 0) return `M ${point.x} ${point.y}`;
      const previous = points[index - 1];
      const beforePrevious = points[index - 2] ?? previous;
      const next = points[index + 1] ?? point;
      const controlOneX = previous.x + (point.x - beforePrevious.x) / 6;
      const controlOneY = previous.y + (point.y - beforePrevious.y) / 6;
      const controlTwoX = point.x - (next.x - previous.x) / 6;
      const controlTwoY = point.y - (next.y - previous.y) / 6;
      return `${path} C ${controlOneX} ${controlOneY}, ${controlTwoX} ${controlTwoY}, ${point.x} ${point.y}`;
    }, '');
  }

  payrollAreaPath(): string {
    const points = this.payrollChartPoints();
    if (points.length === 0) return '';
    const curve = this.payrollCurvePath();
    return `${curve} L ${points[points.length - 1].x} 172 L ${points[0].x} 172 Z`;
  }

  payrollMonthLabel(period: string): string {
    const [year, month] = period.split('-').map(Number);
    return new Date(year, month - 1).toLocaleString('en-US', { month: 'short' });
  }

  attendanceArc(count: number): string {
    const total = this.dashboardSummary()?.attendanceTotalToday ?? 0;
    const length = total > 0 ? 194.8 * count / total : 0;
    return `${length} ${194.8 - length}`;
  }

  attendanceOffset(count: number): number {
    const total = this.dashboardSummary()?.attendanceTotalToday ?? 0;
    return total > 0 ? -194.8 * count / total : 0;
  }

  navigate(page: PageKey): void {
    void this.router.navigate(['/', page]);
  }

  openCreateDialog(): void {
    this.editingEmployee.set(null);
    this.resetForm();
    this.formError.set('');
    this.employeeSuccess.set('');
    this.showEmployeeDialog.set(true);
  }

  viewEmployee(employee: Employee): void {
    this.selectedEmployee.set(employee);
  }

  closeEmployeeDetails(): void {
    this.selectedEmployee.set(null);
  }

  openEditDialog(employee: Employee): void {
    this.selectedEmployee.set(null);
    this.editingEmployee.set(employee);
    this.form.firstName = employee.firstName;
    this.form.lastName = employee.lastName;
    this.form.email = employee.email;
    this.form.department = employee.department ?? '';
    this.form.designation = employee.designation ?? '';
    this.form.salary = employee.salary ?? null;
    this.form.status = employee.status ?? 'ACTIVE';
    this.formError.set('');
    this.employeeSuccess.set('');
    this.showEmployeeDialog.set(true);
  }

  closeEmployeeDialog(): void {
    if (this.saving()) return;
    this.showEmployeeDialog.set(false);
  }

  saveEmployee(): void {
    this.saving.set(true);
    this.formError.set('');
    const payload: EmployeePayload = {
      firstName: this.form.firstName.trim(),
      lastName: this.form.lastName.trim(),
      email: this.form.email.trim(),
      department: (this.form.department ?? '').trim(),
      designation: (this.form.designation ?? '').trim(),
      salary: this.form.salary,
      status: this.form.status,
    };
    const current = this.editingEmployee();
    const request = current
      ? this.employeeService.update(current.id, payload)
      : this.employeeService.create(payload);
    request.subscribe({
      next: (savedEmployee) => {
        this.employees.update((employees) =>
          current
            ? employees.map((employee) =>
                employee.id === savedEmployee.id ? savedEmployee : employee,
              )
            : [savedEmployee, ...employees],
        );
        this.saving.set(false);
        this.showEmployeeDialog.set(false);
        this.resetForm();
        this.employeeSuccess.set(current ? 'Employee record updated.' : 'Employee record created.');
      },
      error: (error: unknown) => {
        console.error('Unable to save employee through the HR API.', error);
        this.formError.set(this.apiErrorMessage(error, 'The employee could not be saved.'));
        this.saving.set(false);
      },
    });
  }

  deleteEmployee(employee: Employee): void {
    if (
      !window.confirm(
        `Delete ${employee.firstName} ${employee.lastName}? This action cannot be undone.`,
      )
    ) {
      return;
    }
    this.pageError.set('');
    this.employeeSuccess.set('');
    this.employeeService.delete(employee.id).subscribe({
      next: () => {
        this.employees.update((employees) =>
          employees.filter((item) => item.id !== employee.id),
        );
        this.employeeSuccess.set('Employee record deleted.');
      },
      error: (error: unknown) => {
        console.error('Unable to delete employee through the HR API.', error);
        this.pageError.set(this.apiErrorMessage(error, 'The employee could not be deleted.'));
      },
    });
  }

  exportCurrentData(): void {
    const page = this.activePage();
    let request: Observable<object[]>;
    switch (page) {
      case 'employees':
        request = this.employeeService.getAll();
        break;
      case 'attendance':
        request = this.hrModulesService.getAttendance();
        break;
      case 'leave':
        request = this.hrModulesService.getLeaveApplications();
        break;
      case 'payroll':
        request = this.hrModulesService.getPayroll();
        break;
      case 'recruitment':
        request = this.hrModulesService.getApplications();
        break;
      case 'analytics':
      case 'dashboard':
        request = this.workspaceService.dashboardSummary().pipe(map((summary) => [summary]));
        break;
      default:
        request = this.workspaceService.list(this.workspaceModuleForPage(page));
        break;
    }
    request.subscribe({
      next: (rows) => {
        if (rows.length === 0) {
          this.pageError.set(`There is no ${this.pageTitles[page].toLowerCase()} data to export.`);
          return;
        }
        const columns = Object.keys(rows[0]);
        const csv = [
          columns,
          ...rows.map((row) => Object.values(row).map((value) => value ?? '')),
        ]
          .map((row) =>
            row.map((value) => `"${String(value).replaceAll('"', '""')}"`).join(','),
          )
          .join('\r\n');
        const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }));
        const anchor = document.createElement('a');
        anchor.href = url;
        anchor.download = `hrgenius-${page}.csv`;
        anchor.click();
        URL.revokeObjectURL(url);
      },
      error: (error: unknown) => {
        console.error(`Unable to export ${page} data.`, error);
        this.pageError.set(this.apiErrorMessage(error, 'The data export failed.'));
      },
    });
  }

  private workspaceModuleForPage(page: PageKey): WorkspaceModule {
    const modules: Partial<Record<PageKey, WorkspaceModule>> = {
      onboarding: 'ONBOARDING',
      performance: 'PERFORMANCE',
      training: 'TRAINING',
      documents: 'DOCUMENTS',
      announcements: 'ANNOUNCEMENTS',
      tasks: 'TASKS',
      settings: 'SETTINGS',
    };
    return modules[page] ?? 'ANNOUNCEMENTS';
  }

  private currentPayrollPeriod(): string {
    const now = new Date();
    return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
  }

  showAuth(page: AuthPage): void {
    this.authNotice.set('');
    this.authError.set('');
    this.resetAuthForm();
    const returnUrl = this.router.parseUrl(this.router.url).queryParams['returnUrl'];
    void this.router.navigate(['/', page === 'forgot' ? 'forgot-password' : page], {
      queryParams: typeof returnUrl === 'string' ? { returnUrl } : {},
    });
  }

  showPasswordResetSetupNotice(): void {
    this.authNotice.set('Password reset isn’t available at the moment. Please contact your HR administrator for help.');
    this.authError.set('');
  }

  submitAuth(form: NgForm): void {
    if (form.invalid || this.authBusy()) {
      form.form.markAllAsTouched();
      return;
    }
    if (this.authPage() === 'forgot') {
      this.authNotice.set('Password reset isn’t available at the moment. Please contact your HR administrator for help.');
      return;
    }
    this.authBusy.set(true);
    this.authError.set('');
    const isSignup = this.authPage() === 'signup';
    const request = isSignup
      ? this.authService.signup({
          name: this.authForm.name.trim(),
          email: this.authForm.email.trim(),
          password: this.authForm.password,
        })
      : this.authService.login({
          email: this.authForm.email.trim(),
          password: this.authForm.password,
        });
    request.subscribe({
      next: () => {
        this.authBusy.set(false);
        const returnUrl = this.router.parseUrl(this.router.url).queryParams['returnUrl'];
        const safeReturnUrl =
          typeof returnUrl === 'string' && returnUrl.startsWith('/') && !returnUrl.startsWith('//')
            ? returnUrl
            : '/dashboard';
        void this.router.navigateByUrl(safeReturnUrl);
      },
      error: (error: unknown) => {
        console.error(`HRGenius ${isSignup ? 'signup' : 'login'} request failed.`, error);
        this.authError.set(this.authErrorMessage(error));
        this.authBusy.set(false);
      },
    });
  }

  logout(): void {
    this.authService.logout();
    this.pageError.set('');
    void this.router.navigate(['/login']);
  }

  isAuthInvalid(
    control: { invalid: boolean | null; touched: boolean | null } | null,
  ): boolean {
    return control?.invalid === true && control.touched === true;
  }

  authTitle(): string {
    switch (this.authPage()) {
      case 'signup':
        return 'Create your account';
      case 'forgot':
        return 'Reset your password';
      default:
        return 'Welcome back';
    }
  }

  private emptyForm(): EmployeeForm {
    return {
      firstName: '',
      lastName: '',
      email: '',
      department: '',
      designation: '',
      salary: null,
      status: 'ACTIVE',
    };
  }

  private resetForm(): void {
    Object.assign(this.form, this.emptyForm());
  }

  private resetAuthForm(): void {
    this.authForm.name = '';
    this.authForm.email = '';
    this.authForm.password = '';
  }

  private syncRoute(): void {
    const path = this.router.url.split('?')[0].split('/').filter(Boolean)[0] ?? 'dashboard';
    const authPages: Record<string, AuthPage> = {
      login: 'login',
      signup: 'signup',
      'forgot-password': 'forgot',
    };
    const authPage = authPages[path] ?? null;
    this.authPage.set(authPage);
    if (authPage) return;

    const page = path as PageKey;
    this.activePage.set(this.pageTitles[page] ? page : 'dashboard');
    if (
      (this.activePage() === 'employees' ||
        (this.activePage() === 'dashboard' && this.isAdmin())) &&
      this.employeeLoadedRoute !== path
    ) {
      this.employeeLoadedRoute = path;
      this.loadEmployees();
    }
    if (this.activePage() === 'dashboard' && this.isAdmin() && !this.dashboardLoaded) {
      this.dashboardLoaded = true;
      this.loadDashboardData();
    }
    if (!this.notifLoaded) {
      this.notifLoaded = true;
      this.notifService.load();
    }
  }

  private authErrorMessage(error: unknown): string {
    if (!(error instanceof HttpErrorResponse)) {
      return 'Authentication could not be completed. Please try again.';
    }
    if (error.status === 0) {
      return 'We couldn’t sign you in right now. Please check your connection and try again.';
    }
    const body: unknown = error.error;
    if (typeof body === 'object' && body !== null) {
      const response = body as { message?: unknown; errors?: unknown };
      if (typeof response.message === 'string') return response.message;
      if (typeof response.errors === 'object' && response.errors !== null) {
        const messages = Object.values(response.errors).filter(
          (value): value is string => typeof value === 'string',
        );
        if (messages.length) return messages.join(' ');
      }

    }
    return 'Authentication failed. Check your details and try again.';
  }

  private apiErrorMessage(error: unknown, fallback: string): string {
    if (!(error instanceof HttpErrorResponse)) return fallback;
    if (error.status === 0) {
      return 'We couldn’t load the latest information. Please try again in a moment.';
    }
    const body: unknown = error.error;
    if (typeof body === 'object' && body !== null) {
      const response = body as { message?: unknown; errors?: unknown };
      if (typeof response.message === 'string') return response.message;
      if (typeof response.errors === 'object' && response.errors !== null) {
        const messages = Object.values(response.errors).filter(
          (value): value is string => typeof value === 'string',
        );
        if (messages.length) return messages.join(' ');
      }
    }
    if (error.status === 403) return 'Your account does not have permission to perform this action.';
    if (error.status === 404) return 'We couldn’t find that employee record.';
    if (error.status === 409) {
      return 'This change conflicts with another HR record. Please review the details or contact HR.';
    }
    return fallback;
  }
}
