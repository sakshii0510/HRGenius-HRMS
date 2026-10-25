import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, Input, OnChanges, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { forkJoin } from 'rxjs';
import { AuthService } from './auth.service';
import { Employee, EmployeeService } from './employee.service';
import { DEPARTMENT_OPTIONS } from './department-options';
import {
  ApplicationStatus,
  AttendancePayload,
  AttendanceRecord,
  AttendanceStatus,
  Candidate,
  CandidatePayload,
  HrModulesService,
  JobApplication,
  JobOpening,
  JobPayload,
  JobStatus,
  LeaveApplication,
  LeavePayload,
  LeaveStatus,
  LeaveType,
  PayrollPayload,
  PayrollPaymentStatus,
  PayrollRecord,
} from './hr-modules.service';

export type HrPage = 'attendance' | 'leave' | 'payroll' | 'recruitment';
type RecruitmentTab = 'jobs' | 'candidates' | 'applications';

@Component({
  selector: 'app-hr-modules',
  imports: [CommonModule, FormsModule],
  templateUrl: './hr-modules.html',
  styleUrl: './hr-modules.css',
})
export class HrModulesComponent implements OnChanges {
  @Input({ required: true }) page!: HrPage;

  private readonly api = inject(HrModulesService);
  private readonly employeeApi = inject(EmployeeService);
  private readonly auth = inject(AuthService);

  readonly isAdmin = computed(() => this.auth.currentUser()?.role === 'ADMIN');
  readonly employees = signal<Employee[]>([]);
  readonly attendance = signal<AttendanceRecord[]>([]);
  readonly leaves = signal<LeaveApplication[]>([]);
  readonly payroll = signal<PayrollRecord[]>([]);
  readonly jobs = signal<JobOpening[]>([]);
  readonly candidates = signal<Candidate[]>([]);
  readonly applications = signal<JobApplication[]>([]);
  readonly openPositionsCount = computed(() =>
    this.jobs().filter((job) => job.status === 'OPEN').length,
  );
  readonly interviewCount = computed(() =>
    this.applications().filter((application) => application.status === 'INTERVIEW').length,
  );
  readonly loading = signal(false);
  readonly saving = signal(false);
  readonly error = signal('');
  readonly success = signal('');
  readonly search = signal('');
  readonly statusFilter = signal('');
  readonly employeeFilter = signal('');
  readonly fromDate = signal('');
  readonly toDate = signal('');
  readonly fromPeriod = signal('');
  readonly toPeriod = signal('');
  readonly tab = signal<RecruitmentTab>('jobs');
  readonly jobFilter = signal('');
  readonly attendanceEditor = signal(false);
  readonly leaveEditor = signal(false);
  readonly payrollEditor = signal(false);
  readonly jobEditor = signal(false);
  readonly candidateEditor = signal(false);
  readonly editingAttendanceId = signal<number | null>(null);
  readonly editingLeaveId = signal<number | null>(null);
  readonly editingPayrollId = signal<number | null>(null);
  readonly editingJobId = signal<number | null>(null);
  readonly editingCandidateId = signal<number | null>(null);
  readonly applyingJob = signal<JobOpening | null>(null);
  readonly payrollSalaryHint = signal('');

  readonly attendanceStatuses: AttendanceStatus[] = [
    'PRESENT',
    'LATE',
    'HALF_DAY',
    'ABSENT',
    'ON_LEAVE',
  ];
  readonly leaveTypes: LeaveType[] = [
    'ANNUAL',
    'SICK',
    'PERSONAL',
    'MATERNITY',
    'PATERNITY',
    'UNPAID',
    'OTHER',
  ];
  readonly leaveStatuses: LeaveStatus[] = ['PENDING', 'APPROVED', 'REJECTED'];
  readonly payrollPaymentStatuses: PayrollPaymentStatus[] = ['PENDING', 'PAID'];
  readonly jobStatuses: JobStatus[] = ['OPEN', 'CLOSED'];
  readonly applicationStatuses: ApplicationStatus[] = [
    'APPLIED',
    'SCREENING',
    'INTERVIEW',
    'OFFERED',
    'REJECTED',
    'WITHDRAWN',
  ];
  readonly employmentTypes = ['Full-time', 'Part-time', 'Contract', 'Temporary', 'Internship'];
  readonly departmentOptions = computed(() => [
    ...new Set([
      ...DEPARTMENT_OPTIONS,
      ...this.employees()
        .map((employee) => employee.department?.trim())
        .filter((department): department is string => Boolean(department)),
      ...this.jobs().map((job) => job.department.trim()).filter(Boolean),
    ]),
  ]);

  readonly attendanceForm = {
    employeeId: 0,
    attendanceDate: this.today(),
    checkIn: '',
    checkOut: '',
    status: 'PRESENT' as AttendanceStatus,
  };
  readonly leaveForm = {
    employeeId: 0,
    leaveType: 'ANNUAL' as LeaveType,
    startDate: this.today(),
    endDate: this.today(),
    reason: '',
  };
  readonly payrollForm = {
    employeeId: 0,
    salaryPeriod: this.currentPeriod(),
    basicSalary: 0,
    allowances: 0,
    deductions: 0,
    paymentStatus: 'PENDING' as PayrollPaymentStatus,
  };
  readonly jobForm = {
    title: '',
    department: '',
    location: '',
    employmentType: 'Full-time',
    description: '',
    salaryMin: null as number | null,
    salaryMax: null as number | null,
    status: 'OPEN' as JobStatus,
  };
  readonly candidateForm = { name: '', email: '', phone: '' };
  readonly applicationForm = { phone: '', coverLetter: '' };
  readonly applicationStatusSelection: Record<number, ApplicationStatus> = {};
  reviewNote = '';

  readonly filteredAttendance = computed(() => {
    const term = this.search().trim().toLowerCase();
    return this.attendance().filter((item) =>
      !term ||
      `${item.employeeName} ${item.employeeId} ${item.status} ${item.attendanceDate}`
        .toLowerCase()
        .includes(term),
    );
  });
  readonly filteredLeaves = computed(() => {
    const term = this.search().trim().toLowerCase();
    return this.leaves().filter((item) =>
      !term ||
      `${item.employeeName} ${item.employeeId} ${item.leaveType} ${item.reason}`
        .toLowerCase()
        .includes(term),
    );
  });
  readonly filteredPayroll = computed(() => {
    const term = this.search().trim().toLowerCase();
    return this.payroll().filter((item) =>
      !term ||
      `${item.employeeName} ${item.employeeId} ${item.salaryPeriod}`.toLowerCase().includes(term),
    );
  });
  readonly filteredJobs = computed(() => {
    const term = this.search().trim().toLowerCase();
    return this.jobs().filter((item) =>
      !term ||
      `${item.title} ${item.department} ${item.location} ${item.employmentType}`
        .toLowerCase()
        .includes(term),
    );
  });
  readonly filteredCandidates = computed(() => {
    const term = this.search().trim().toLowerCase();
    return this.candidates().filter((item) =>
      !term || `${item.name} ${item.email} ${item.phone ?? ''}`.toLowerCase().includes(term),
    );
  });
  readonly filteredApplications = computed(() => {
    const term = this.search().trim().toLowerCase();
    return this.applications().filter((item) =>
      !term ||
      `${item.jobTitle} ${item.candidateName} ${item.candidateEmail} ${item.status}`
        .toLowerCase()
        .includes(term),
    );
  });

  ngOnChanges(): void {
    this.statusFilter.set('');
    this.employeeFilter.set('');
    this.jobFilter.set('');
    this.fromDate.set('');
    this.toDate.set('');
    this.fromPeriod.set('');
    this.toPeriod.set('');
    this.search.set('');
    this.load();
  }

  applicationStatusForJob(job: JobOpening): ApplicationStatus | null {
    return (
      this.applications().find((application) => application.jobOpeningId === job.id)?.status ?? null
    );
  }

  load(): void {
    this.loading.set(true);
    this.error.set('');
    const filters = this.moduleFilters();
    if (this.page === 'recruitment') {
      const jobStatus = this.jobStatuses.find((status) => status === this.statusFilter());
      const applicationStatus = this.applicationStatuses.find(
        (status) => status === this.statusFilter(),
      );
      const jobFilters = this.tab() === 'jobs' ? jobStatus : undefined;
      const applicationFilters: Record<string, string> = {};
      if (this.tab() === 'applications' && this.isAdmin() && this.jobFilter()) {
        applicationFilters['jobId'] = this.jobFilter();
      }
      if (this.tab() === 'applications' && applicationStatus) {
        applicationFilters['status'] = applicationStatus;
      }
      if (this.tab() === 'applications' && this.search().trim()) {
        applicationFilters['search'] = this.search().trim();
      }
      if (this.isAdmin()) {
        forkJoin({
          jobs: this.api.getJobs(this.tab() === 'jobs' ? this.search() : '', jobFilters),
          candidates: this.api.getCandidates(this.tab() === 'candidates' ? this.search() : ''),
          applications: this.api.getApplications(applicationFilters),
          employees: this.employeeApi.getAll(),
        }).subscribe({
          next: (data) => {
            this.jobs.set(data.jobs);
            this.candidates.set(data.candidates);
            this.applications.set(data.applications);
            this.employees.set(data.employees);
            this.finishLoad();
          },
          error: (error: unknown) => this.failLoad(error),
        });
      } else {
        forkJoin({
          jobs: this.api.getJobs(this.tab() === 'jobs' ? this.search() : '', jobFilters),
          applications: this.api.getApplications(applicationFilters),
          employees: this.employeeApi.getAll(),
        }).subscribe({
          next: (data) => {
            this.jobs.set(data.jobs);
            this.applications.set(data.applications);
            this.employees.set(data.employees);
            this.finishLoad();
          },
          error: (error: unknown) => this.failLoad(error),
        });
      }
      return;
    }

    const employeeRequest = this.employeeApi.getAll();
    const moduleRequest =
      this.page === 'attendance'
        ? this.api.getAttendance(filters)
        : this.page === 'leave'
          ? this.api.getLeaveApplications(filters)
          : this.api.getPayroll(filters);
    forkJoin({ employees: employeeRequest, records: moduleRequest }).subscribe({
      next: ({ employees, records }) => {
        this.employees.set(employees);
        this.setCurrentEmployeeId(employees);
        if (this.page === 'attendance') this.attendance.set(records as AttendanceRecord[]);
        else if (this.page === 'leave') this.leaves.set(records as LeaveApplication[]);
        else this.payroll.set(records as PayrollRecord[]);
        this.finishLoad();
      },
      error: (error: unknown) => this.failLoad(error),
    });
  }

  startAttendanceCreate(): void {
    this.editingAttendanceId.set(null);
    Object.assign(this.attendanceForm, {
      employeeId: this.defaultEmployeeId(),
      attendanceDate: this.today(),
      checkIn: '',
      checkOut: '',
      status: 'PRESENT' as AttendanceStatus,
    });
    this.attendanceEditor.set(true);
    this.clearFeedback();
  }

  editAttendance(record: AttendanceRecord): void {
    this.editingAttendanceId.set(record.id);
    Object.assign(this.attendanceForm, {
      employeeId: record.employeeId,
      attendanceDate: record.attendanceDate,
      checkIn: record.checkIn ?? '',
      checkOut: record.checkOut ?? '',
      status: record.status,
    });
    this.attendanceEditor.set(true);
    this.clearFeedback();
  }

  saveAttendance(): void {
    this.saving.set(true);
    this.clearFeedback();
    const payload: AttendancePayload = {
      ...this.attendanceForm,
      checkIn: this.attendanceForm.checkIn || null,
      checkOut: this.attendanceForm.checkOut || null,
    };
    const id = this.editingAttendanceId();
    const request = id
      ? this.api.updateAttendance(id, payload)
      : this.api.createAttendance(payload);
    request.subscribe({
      next: (saved) => {
        this.attendance.update((records) =>
          id
            ? records.map((item) => (item.id === saved.id ? saved : item))
            : [saved, ...records],
        );
        this.attendanceEditor.set(false);
        this.finishSave(id ? 'Attendance record updated.' : 'Attendance recorded.');
      },
      error: (error: unknown) => this.failSave(error, 'Attendance could not be saved.'),
    });
  }

  deleteAttendance(record: AttendanceRecord): void {
    if (!window.confirm(`Delete attendance for ${record.employeeName} on ${record.attendanceDate}?`)) {
      return;
    }
    this.runDelete(
      this.api.deleteAttendance(record.id),
      () => this.attendance.update((items) => items.filter((item) => item.id !== record.id)),
      'Attendance record deleted.',
    );
  }

  startLeaveCreate(): void {
    this.editingLeaveId.set(null);
    Object.assign(this.leaveForm, {
      employeeId: this.defaultEmployeeId(),
      leaveType: 'ANNUAL' as LeaveType,
      startDate: this.today(),
      endDate: this.today(),
      reason: '',
    });
    this.leaveEditor.set(true);
    this.clearFeedback();
  }

  editLeave(leave: LeaveApplication): void {
    this.editingLeaveId.set(leave.id);
    Object.assign(this.leaveForm, {
      employeeId: leave.employeeId,
      leaveType: leave.leaveType,
      startDate: leave.startDate,
      endDate: leave.endDate,
      reason: leave.reason,
    });
    this.leaveEditor.set(true);
    this.clearFeedback();
  }

  saveLeave(): void {
    this.saving.set(true);
    this.clearFeedback();
    const payload: LeavePayload = { ...this.leaveForm };
    const id = this.editingLeaveId();
    const request = id ? this.api.updateLeave(id, payload) : this.api.createLeave(payload);
    request.subscribe({
      next: (saved) => {
        this.leaves.update((items) =>
          id ? items.map((item) => (item.id === saved.id ? saved : item)) : [saved, ...items],
        );
        this.leaveEditor.set(false);
        this.finishSave(id ? 'Leave application updated.' : 'Leave application submitted.');
      },
      error: (error: unknown) => this.failSave(error, 'Leave application could not be saved.'),
    });
  }

  deleteLeave(leave: LeaveApplication): void {
    if (!window.confirm('Delete this pending leave application?')) return;
    this.runDelete(
      this.api.deleteLeave(leave.id),
      () => this.leaves.update((items) => items.filter((item) => item.id !== leave.id)),
      'Leave application deleted.',
    );
  }

  reviewLeave(leave: LeaveApplication, status: 'APPROVED' | 'REJECTED'): void {
    this.saving.set(true);
    this.clearFeedback();
    this.api.reviewLeave(leave.id, status, this.reviewNote.trim()).subscribe({
      next: (updated) => {
        this.leaves.update((items) =>
          items.map((item) => (item.id === updated.id ? updated : item)),
        );
        this.finishSave(`Leave application ${status.toLowerCase()}.`);
      },
      error: (error: unknown) => this.failSave(error, 'Leave application could not be reviewed.'),
    });
  }

  startPayrollCreate(): void {
    this.editingPayrollId.set(null);
    Object.assign(this.payrollForm, {
      employeeId: this.defaultEmployeeId(),
      salaryPeriod: this.currentPeriod(),
      basicSalary: 0,
      allowances: 0,
      deductions: 0,
      paymentStatus: 'PENDING' as PayrollPaymentStatus,
    });
    this.selectPayrollEmployee(this.payrollForm.employeeId);
    this.payrollEditor.set(true);
    this.clearFeedback();
  }

  editPayroll(record: PayrollRecord): void {
    this.editingPayrollId.set(record.id);
    this.payrollSalaryHint.set('');
    Object.assign(this.payrollForm, {
      employeeId: record.employeeId,
      salaryPeriod: record.salaryPeriod,
      basicSalary: record.basicSalary,
      allowances: record.allowances,
      deductions: record.deductions,
      paymentStatus: record.paymentStatus,
    });
    this.payrollEditor.set(true);
    this.clearFeedback();
  }

  savePayroll(): void {
    this.saving.set(true);
    this.clearFeedback();
    const payload: PayrollPayload = { ...this.payrollForm };
    const id = this.editingPayrollId();
    const request = id ? this.api.updatePayroll(id, payload) : this.api.createPayroll(payload);
    request.subscribe({
      next: (saved) => {
        this.payroll.update((items) =>
          id ? items.map((item) => (item.id === saved.id ? saved : item)) : [saved, ...items],
        );
        this.payrollEditor.set(false);
        this.finishSave(id ? 'Payroll record updated.' : 'Payroll record created.');
      },
      error: (error: unknown) => this.failSave(error, 'Payroll record could not be saved.'),
    });
  }

  selectPayrollEmployee(employeeId: number): void {
    const employee = this.employees().find((item) => item.id === Number(employeeId));
    if (!employee || employee.salary == null) {
      this.payrollForm.basicSalary = 0;
      this.payrollSalaryHint.set(
        employee ? 'No annual salary is set on this employee profile. Enter the monthly basic salary.' :
          'Select an employee to fill the monthly basic salary from their annual salary.',
      );
      return;
    }
    this.payrollForm.basicSalary = Math.round((employee.salary / 12) * 100) / 100;
    this.payrollSalaryHint.set(
      `Monthly amount filled from ${employee.firstName}'s annual salary (${employee.salary.toLocaleString()} ÷ 12). You can adjust it if needed.`,
    );
  }

  invalidSalaryRange(): boolean {
    return this.jobForm.salaryMin != null
      && this.jobForm.salaryMax != null
      && this.jobForm.salaryMin > this.jobForm.salaryMax;
  }

  setRecruitmentTab(tab: RecruitmentTab): void {
    this.tab.set(tab);
    this.statusFilter.set('');
    this.jobFilter.set('');
    this.search.set('');
    this.load();
  }

  showJobApplications(job: JobOpening): void {
    this.tab.set('applications');
    this.statusFilter.set('');
    this.search.set('');
    this.jobFilter.set(String(job.id));
    this.load();
  }

  applicationCount(jobId: number): number {
    return this.applications().filter((application) => application.jobOpeningId === jobId).length;
  }

  deletePayroll(record: PayrollRecord): void {
    if (!window.confirm(`Delete payroll for ${record.employeeName} (${record.salaryPeriod})?`)) return;
    this.runDelete(
      this.api.deletePayroll(record.id),
      () => this.payroll.update((items) => items.filter((item) => item.id !== record.id)),
      'Payroll record deleted.',
    );
  }

  startJobCreate(): void {
    this.editingJobId.set(null);
    Object.assign(this.jobForm, {
      title: '',
      department: '',
      location: '',
      employmentType: 'Full-time',
      description: '',
      salaryMin: null,
      salaryMax: null,
      status: 'OPEN' as JobStatus,
    });
    this.jobEditor.set(true);
    this.clearFeedback();
  }

  editJob(job: JobOpening): void {
    this.editingJobId.set(job.id);
    Object.assign(this.jobForm, {
      title: job.title,
      department: job.department,
      location: job.location,
      employmentType: job.employmentType,
      description: job.description,
      salaryMin: job.salaryMin,
      salaryMax: job.salaryMax,
      status: job.status,
    });
    this.jobEditor.set(true);
    this.clearFeedback();
  }

  saveJob(): void {
    this.saving.set(true);
    this.clearFeedback();
    const payload: JobPayload = { ...this.jobForm };
    const id = this.editingJobId();
    const request = id ? this.api.updateJob(id, payload) : this.api.createJob(payload);
    request.subscribe({
      next: (saved) => {
        this.jobs.update((items) =>
          id ? items.map((item) => (item.id === saved.id ? saved : item)) : [saved, ...items],
        );
        this.jobEditor.set(false);
        this.finishSave(id ? 'Job opening updated.' : 'Job opening created.');
      },
      error: (error: unknown) => this.failSave(error, 'Job opening could not be saved.'),
    });
  }

  deleteJob(job: JobOpening): void {
    if (!window.confirm(`Delete the "${job.title}" job opening?`)) return;
    this.runDelete(
      this.api.deleteJob(job.id),
      () => this.jobs.update((items) => items.filter((item) => item.id !== job.id)),
      'Job opening deleted.',
    );
  }

  startCandidateCreate(): void {
    this.editingCandidateId.set(null);
    Object.assign(this.candidateForm, { name: '', email: '', phone: '' });
    this.candidateEditor.set(true);
    this.clearFeedback();
  }

  editCandidate(candidate: Candidate): void {
    this.editingCandidateId.set(candidate.id);
    Object.assign(this.candidateForm, {
      name: candidate.name,
      email: candidate.email,
      phone: candidate.phone ?? '',
    });
    this.candidateEditor.set(true);
    this.clearFeedback();
  }

  saveCandidate(): void {
    this.saving.set(true);
    this.clearFeedback();
    const payload: CandidatePayload = {
      ...this.candidateForm,
      name: this.candidateForm.name.trim(),
      email: this.candidateForm.email.trim(),
      phone: this.candidateForm.phone.trim() || null,
    };
    const id = this.editingCandidateId();
    const request = id
      ? this.api.updateCandidate(id, payload)
      : this.api.createCandidate(payload);
    request.subscribe({
      next: (saved) => {
        this.candidates.update((items) =>
          id ? items.map((item) => (item.id === saved.id ? saved : item)) : [saved, ...items],
        );
        this.candidateEditor.set(false);
        this.finishSave(id ? 'Candidate updated.' : 'Candidate created.');
      },
      error: (error: unknown) => this.failSave(error, 'Candidate could not be saved.'),
    });
  }

  deleteCandidate(candidate: Candidate): void {
    if (!window.confirm(`Delete candidate ${candidate.name}?`)) return;
    this.runDelete(
      this.api.deleteCandidate(candidate.id),
      () => this.candidates.update((items) => items.filter((item) => item.id !== candidate.id)),
      'Candidate deleted.',
    );
  }

  beginApplication(job: JobOpening): void {
    this.applyingJob.set(job);
    Object.assign(this.applicationForm, { phone: '', coverLetter: '' });
    this.clearFeedback();
  }

  applyForJob(): void {
    const job = this.applyingJob();
    if (!job) return;
    this.saving.set(true);
    this.clearFeedback();
    this.api
      .applyForJob(job.id, {
        phone: this.applicationForm.phone.trim() || null,
        coverLetter: this.applicationForm.coverLetter.trim() || null,
      })
      .subscribe({
        next: (application) => {
          this.applications.update((items) => [application, ...items]);
          this.applyingJob.set(null);
          this.finishSave('Application submitted.');
        },
        error: (error: unknown) => this.failSave(error, 'Application could not be submitted.'),
      });
  }

  canApply(job: JobOpening): boolean {
    return (
      !this.isAdmin() &&
      job.status === 'OPEN' &&
      !this.applications().some((application) => application.jobOpeningId === job.id)
    );
  }

  transitionOptions(status: ApplicationStatus): ApplicationStatus[] {
    switch (status) {
      case 'APPLIED':
        return ['SCREENING', 'REJECTED'];
      case 'SCREENING':
        return ['INTERVIEW', 'OFFERED', 'REJECTED'];
      case 'INTERVIEW':
        return ['OFFERED', 'REJECTED'];
      default:
        return [];
    }
  }

  setApplicationStatus(applicationId: number, status: ApplicationStatus): void {
    this.applicationStatusSelection[applicationId] = status;
  }

  saveApplicationStatus(application: JobApplication): void {
    const status = this.applicationStatusSelection[application.id];
    if (!status || !this.transitionOptions(application.status).includes(status)) return;
    this.saving.set(true);
    this.clearFeedback();
    this.api.updateApplicationStatus(application.id, status).subscribe({
      next: (updated) => {
        this.applications.update((items) =>
          items.map((item) => (item.id === updated.id ? updated : item)),
        );
        delete this.applicationStatusSelection[application.id];
        this.finishSave('Application status updated.');
      },
      error: (error: unknown) => this.failSave(error, 'Application status could not be updated.'),
    });
  }

  withdrawApplication(application: JobApplication): void {
    if (!window.confirm(`Withdraw your application for "${application.jobTitle}"?`)) return;
    this.runDelete(
      this.api.withdrawApplication(application.id),
      () =>
        this.applications.update((items) =>
          items.filter((item) => item.id !== application.id),
        ),
      'Application withdrawn.',
    );
  }

  closeEditors(): void {
    if (this.saving()) return;
    this.attendanceEditor.set(false);
    this.leaveEditor.set(false);
    this.payrollEditor.set(false);
    this.jobEditor.set(false);
    this.candidateEditor.set(false);
    this.applyingJob.set(null);
  }

  private moduleFilters(): Record<string, string> {
    const filters: Record<string, string> = {};
    if (this.isAdmin() && this.employeeFilter()) filters['employeeId'] = this.employeeFilter();
    if (this.statusFilter()) filters['status'] = this.statusFilter();
    if (this.page === 'attendance' || this.page === 'leave') {
      if (this.fromDate()) filters['fromDate'] = this.fromDate();
      if (this.toDate()) filters['toDate'] = this.toDate();
    }
    if (this.page === 'payroll') {
      delete filters['status'];
      if (this.fromPeriod()) filters['fromPeriod'] = this.fromPeriod();
      if (this.toPeriod()) filters['toPeriod'] = this.toPeriod();
    }
    return filters;
  }

  private setCurrentEmployeeId(employees: Employee[]): void {
    if (employees.length === 0) return;
    const id = employees[0].id;
    if (!this.isAdmin()) {
      this.attendanceForm.employeeId = id;
      this.leaveForm.employeeId = id;
      this.payrollForm.employeeId = id;
    } else if (!this.attendanceForm.employeeId) {
      this.attendanceForm.employeeId = id;
      this.leaveForm.employeeId = id;
      this.payrollForm.employeeId = id;
    }
  }

  private defaultEmployeeId(): number {
    return this.employees()[0]?.id ?? 0;
  }

  today(): string {
    const now = new Date();
    return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
  }

  private currentPeriod(): string {
    const now = new Date();
    return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
  }

  private finishLoad(): void {
    this.loading.set(false);
  }

  private failLoad(error: unknown): void {
    console.error(`Unable to load ${this.page} data from the HR API.`, error);
    this.error.set(this.errorMessage(error, `Could not load ${this.page} records.`));
    this.loading.set(false);
  }

  private finishSave(message: string): void {
    this.saving.set(false);
    this.success.set(message);
  }

  private failSave(error: unknown, fallback: string): void {
    console.error(`HR ${this.page} operation failed.`, error);
    this.error.set(this.errorMessage(error, fallback));
    this.saving.set(false);
  }

  private runDelete(
    request: import('rxjs').Observable<void>,
    onSuccess: () => void,
    message: string,
  ): void {
    this.saving.set(true);
    this.clearFeedback();
    request.subscribe({
      next: () => {
        onSuccess();
        this.finishSave(message);
      },
      error: (error: unknown) => this.failSave(error, 'The record could not be deleted.'),
    });
  }

  clearFeedback(): void {
    this.error.set('');
    this.success.set('');
  }

  private errorMessage(error: unknown, fallback: string): string {
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
    if (error.status === 404) return 'The requested record was not found.';
    if (error.status === 409) {
      return 'This change conflicts with existing data or a related record. Review the record and try again.';
    }
    return fallback;
  }
}
