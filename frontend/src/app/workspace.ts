import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, Input, OnChanges, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { forkJoin } from 'rxjs';
import { AuthService } from './auth.service';
import { Employee, EmployeeService } from './employee.service';
import {
  DashboardSummary,
  WorkspaceModule,
  WorkspacePayload,
  WorkspaceRecord,
  WorkspaceService,
} from './workspace.service';

export type WorkspacePage =
  | 'onboarding'
  | 'performance'
  | 'training'
  | 'documents'
  | 'announcements'
  | 'analytics'
  | 'tasks';

const MODULE_BY_PAGE: Record<Exclude<WorkspacePage, 'analytics'>, WorkspaceModule> = {
  onboarding: 'ONBOARDING',
  performance: 'PERFORMANCE',
  training: 'TRAINING',
  documents: 'DOCUMENTS',
  announcements: 'ANNOUNCEMENTS',
  tasks: 'TASKS',
};

@Component({
  selector: 'app-workspace',
  imports: [CommonModule, FormsModule],
  templateUrl: './workspace.html',
  styleUrl: './workspace.css',
})
export class WorkspaceComponent implements OnChanges {
  @Input({ required: true }) page!: WorkspacePage;

  private readonly workspaceApi = inject(WorkspaceService);
  private readonly employeeApi = inject(EmployeeService);
  private readonly auth = inject(AuthService);

  readonly isAdmin = computed(() => this.auth.currentUser()?.role === 'ADMIN');
  readonly records = signal<WorkspaceRecord[]>([]);
  readonly employees = signal<Employee[]>([]);
  readonly summary = signal<DashboardSummary | null>(null);
  readonly loading = signal(false);
  readonly saving = signal(false);
  readonly error = signal('');
  readonly success = signal('');
  readonly search = signal('');
  readonly statusFilter = signal('');
  readonly employeeFilter = signal('');
  readonly editorOpen = signal(false);
  readonly editingId = signal<number | null>(null);
  readonly selectedFile = signal<File | null>(null);
  readonly module = computed<WorkspaceModule | null>(() =>
    this.page === 'analytics' ? null : MODULE_BY_PAGE[this.page],
  );
  readonly ownEmployeeId = computed(() => {
    const email = this.auth.currentUser()?.email.toLowerCase();
    return this.employees().find((employee) => employee.email.toLowerCase() === email)?.id ?? null;
  });
  readonly filteredRecords = computed(() => {
    const term = this.search().trim().toLowerCase();
    const status = this.statusFilter().toUpperCase();
    return this.records().filter((record) => {
      const matchesText =
        !term ||
        `${record.title} ${record.description ?? ''} ${record.employeeName ?? ''} ${record.status}`
          .toLowerCase()
          .includes(term);
      const matchesStatus = !status || record.status.toUpperCase() === status;
      const matchesEmployee =
        !this.isAdmin() ||
        !this.employeeFilter() ||
        String(record.employeeId ?? '') === this.employeeFilter();
      return matchesText && matchesStatus && matchesEmployee;
    });
  });

  readonly form = {
    title: '',
    description: '',
    status: '',
    employeeId: 0,
    dueDate: '',
    score: null as number | null,
    visibleToEmployees: false,
  };

  private readonly statuses: Record<Exclude<WorkspacePage, 'analytics'>, string[]> = {
    onboarding: ['TO_DO', 'IN_PROGRESS', 'COMPLETED'],
    performance: ['DRAFT', 'PUBLISHED', 'ACKNOWLEDGED'],
    training: ['OPEN', 'PUBLISHED', 'IN_PROGRESS', 'COMPLETED'],
    documents: ['DRAFT', 'PUBLISHED', 'ARCHIVED'],
    announcements: ['DRAFT', 'PUBLISHED', 'ARCHIVED'],
    tasks: ['TO_DO', 'IN_PROGRESS', 'COMPLETED'],
  };

  ngOnChanges(): void {
    this.search.set('');
    this.statusFilter.set('');
    this.employeeFilter.set('');
    this.load();
  }

  availableStatuses(): string[] {
    return this.page === 'analytics' ? [] : this.statuses[this.page];
  }

  load(): void {
    this.loading.set(true);
    this.error.set('');
    if (this.page === 'analytics') {
      this.workspaceApi.dashboardSummary().subscribe({
        next: (summary) => {
          this.summary.set(summary);
          this.loading.set(false);
        },
        error: (error: unknown) => this.fail(error, 'Analytics could not be loaded.'),
      });
      return;
    }
    const module = this.module();
    if (!module) return;
    const filters: Record<string, string> = {};
    if (this.isAdmin() && this.employeeFilter()) filters['employeeId'] = this.employeeFilter();
    if (this.statusFilter()) filters['status'] = this.statusFilter();
    if (this.search().trim()) filters['search'] = this.search().trim();
    forkJoin({
      records: this.workspaceApi.list(module, filters),
      employees: this.employeeApi.getAll(),
    }).subscribe({
      next: ({ records, employees }) => {
        this.records.set(records);
        this.employees.set(employees);
        this.loading.set(false);
      },
      error: (error: unknown) => this.fail(error, `Could not load ${this.page} records.`),
    });
  }

  startCreate(): void {
    this.editingId.set(null);
    this.selectedFile.set(null);
    Object.assign(this.form, {
      title: '',
      description: '',
      status: this.defaultStatus(),
      employeeId: this.isAdmin() ? 0 : (this.ownEmployeeId() ?? 0),
      dueDate: '',
      score: this.page === 'performance' ? 0 : null,
      visibleToEmployees: this.page === 'announcements' || this.page === 'training',
    });
    this.editorOpen.set(true);
    this.clearFeedback();
  }

  editRecord(record: WorkspaceRecord): void {
    this.editingId.set(record.id);
    this.selectedFile.set(null);
    Object.assign(this.form, {
      title: record.title,
      description: record.description ?? '',
      status: record.status,
      employeeId: record.employeeId ?? 0,
      dueDate: record.dueDate ?? '',
      score: record.score,
      visibleToEmployees: record.visibleToEmployees,
    });
    this.editorOpen.set(true);
    this.clearFeedback();
  }

  onFileChange(event: Event): void {
    const input = event.target;
    if (!(input instanceof HTMLInputElement)) return;
    this.selectedFile.set(input.files?.[0] ?? null);
  }

  save(): void {
    const module = this.module();
    if (!module) return;
    if (this.page === 'documents' && !this.editingId() && !this.selectedFile()) {
      this.error.set('Choose a document file before saving.');
      return;
    }
    if (this.page === 'performance' && this.form.score == null) {
      this.error.set('Enter a performance score between 0 and 100.');
      return;
    }
    if (this.needsEmployee() && this.form.employeeId <= 0) {
      this.error.set('Select an employee for this record.');
      return;
    }
    const payload: WorkspacePayload = {
      title: this.form.title.trim(),
      description: this.form.description.trim(),
      status: this.form.status,
      employeeId: this.needsEmployee() ? this.form.employeeId : null,
      dueDate: this.form.dueDate || null,
      score: this.page === 'performance' ? this.form.score : null,
      visibleToEmployees: this.page === 'announcements' || this.page === 'training'
        ? this.form.visibleToEmployees
        : false,
    };
    const id = this.editingId();
    const file = this.selectedFile();
    this.saving.set(true);
    this.clearFeedback();
    const request = id
      ? this.workspaceApi.update(module, id, payload)
      : this.page === 'documents' && file
        ? this.workspaceApi.uploadDocument(payload, file)
        : this.workspaceApi.create(module, payload);
    request.subscribe({
      next: (record) => {
        this.records.update((items) =>
          id ? items.map((item) => (item.id === record.id ? record : item)) : [record, ...items],
        );
        this.editorOpen.set(false);
        this.finish(id ? 'Changes saved.' : `${this.pageLabel()} record created.`);
      },
      error: (error: unknown) => this.fail(error, 'The record could not be saved.'),
    });
  }

  deleteRecord(record: WorkspaceRecord): void {
    const module = this.module();
    if (!module || !window.confirm(`Delete "${record.title}"? This cannot be undone.`)) return;
    this.saving.set(true);
    this.clearFeedback();
    this.workspaceApi.delete(module, record.id).subscribe({
      next: () => {
        this.records.update((items) => items.filter((item) => item.id !== record.id));
        this.finish('Record deleted.');
      },
      error: (error: unknown) => this.fail(error, 'The record could not be deleted.'),
    });
  }

  canUpdateStatus(record: WorkspaceRecord): boolean {
    if (this.isAdmin()) return true;
    return (
      ['ONBOARDING', 'TASKS', 'TRAINING', 'PERFORMANCE'].includes(record.module) &&
      record.employeeId !== null &&
      record.employeeId === this.ownEmployeeId()
    );
  }

  nextStatus(record: WorkspaceRecord): string | null {
    if (record.module === 'TRAINING') {
      if (record.status === 'IN_PROGRESS' || record.status === 'ENROLLED') return 'COMPLETED';
      if (this.isAdmin() && record.status === 'OPEN') return 'PUBLISHED';
      return null;
    }
    if (record.module === 'PERFORMANCE') {
      if (record.status === 'DRAFT' && this.isAdmin()) return 'PUBLISHED';
      if (record.status === 'PUBLISHED') return this.isAdmin() ? 'ACKNOWLEDGED' : 'ACKNOWLEDGED';
      return null;
    }
    if (record.module === 'DOCUMENTS' || record.module === 'ANNOUNCEMENTS') {
      if (!this.isAdmin()) return null;
      if (record.status === 'DRAFT') return 'PUBLISHED';
      if (record.status === 'PUBLISHED') return 'ARCHIVED';
      return null;
    }
    if (record.status === 'TO_DO') return 'IN_PROGRESS';
    if (record.status === 'IN_PROGRESS') return 'COMPLETED';
    if (this.isAdmin() && ['DRAFT', 'ARCHIVED'].includes(record.status)) return 'PUBLISHED';
    if (this.isAdmin() && record.status === 'PUBLISHED') return 'ARCHIVED';
    return null;
  }

  isEnrolled(course: WorkspaceRecord): boolean {
    const employeeId = this.ownEmployeeId();
    return this.records().some(
      (record) =>
        record.module === 'TRAINING' &&
        record.employeeId === employeeId &&
        record.title.toLowerCase() === course.title.toLowerCase() &&
        record.id !== course.id,
    );
  }

  canEnroll(course: WorkspaceRecord): boolean {
    return (
      !this.isAdmin() &&
      course.employeeId === null &&
      course.visibleToEmployees &&
      ['OPEN', 'PUBLISHED'].includes(course.status) &&
      !this.isEnrolled(course)
    );
  }

  enroll(course: WorkspaceRecord): void {
    this.saving.set(true);
    this.clearFeedback();
    this.workspaceApi.enrollTraining(course.id).subscribe({
      next: (enrollment) => {
        this.records.update((records) => [enrollment, ...records]);
        this.finish('You are enrolled in this training course.');
      },
      error: (error: unknown) => this.fail(error, 'Training enrollment could not be completed.'),
    });
  }

  updateStatus(record: WorkspaceRecord, status: string): void {
    this.saving.set(true);
    this.clearFeedback();
    this.workspaceApi.updateStatus(record.module, record.id, status).subscribe({
      next: (updated) => {
        this.records.update((items) =>
          items.map((item) => (item.id === updated.id ? updated : item)),
        );
        this.finish(`Status changed to ${status.replaceAll('_', ' ').toLowerCase()}.`);
      },
      error: (error: unknown) => this.fail(error, 'The status could not be updated.'),
    });
  }

  download(record: WorkspaceRecord): void {
    this.workspaceApi.downloadDocument(record.id).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = record.fileName || record.title;
        link.click();
        URL.revokeObjectURL(url);
      },
      error: (error: unknown) => this.fail(error, 'The document could not be downloaded.'),
    });
  }

  closeEditor(): void {
    if (!this.saving()) this.editorOpen.set(false);
  }

  moduleLabel(): string {
    return this.page === 'analytics'
      ? 'Reports & Analytics'
      : this.page === 'announcements'
          ? 'Announcements'
          : this.page === 'performance'
            ? 'Performance'
            : this.page === 'training'
              ? 'Training & Development'
              : this.page.charAt(0).toUpperCase() + this.page.slice(1);
  }

  pageDescription(): string {
    const descriptions: Record<WorkspacePage, string> = {
      onboarding: 'Assign and complete employee onboarding checklists.',
      performance: 'Manage employee performance reviews and scores.',
      training: 'Publish courses and track assigned training progress.',
      documents: 'Upload, organize and retrieve employee documents.',
      announcements: 'Publish organization announcements for employees.',
      analytics: 'Live workforce totals derived from saved HR records.',
      tasks: 'Assign tasks and track completion from employee accounts.',
    };
    return descriptions[this.page];
  }

  maxHistoryAmount(history: DashboardSummary['payrollHistory']): number {
    return Math.max(...history.map((month) => month.total), 0);
  }

  private defaultStatus(): string {
    switch (this.page) {
      case 'performance':
      case 'documents':
      case 'announcements':
        return 'DRAFT';
      case 'training':
        return 'OPEN';
      default:
        return 'TO_DO';
    }
  }

  private needsEmployee(): boolean {
    return ['onboarding', 'performance', 'tasks'].includes(this.page) ||
      (this.page === 'documents' && this.form.employeeId > 0) ||
      (this.page === 'training' && this.form.employeeId > 0);
  }

  private pageLabel(): string {
    return this.moduleLabel().replace(/s$/, '');
  }

  private finish(message: string): void {
    this.saving.set(false);
    this.success.set(message);
  }

  private clearFeedback(): void {
    this.error.set('');
    this.success.set('');
  }

  private fail(error: unknown, fallback: string): void {
    console.error(`Workspace ${this.page} request failed.`, error);
    this.error.set(this.errorMessage(error, fallback));
    this.loading.set(false);
    this.saving.set(false);
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
    if (error.status === 409) return 'This record conflicts with related or existing data.';
    if (error.status === 413) return 'The uploaded file is larger than the 10 MB limit.';
    return fallback;
  }
}
