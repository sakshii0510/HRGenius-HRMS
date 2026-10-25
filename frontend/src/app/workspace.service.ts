import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from './api-url';

export type WorkspaceModule =
  | 'ONBOARDING'
  | 'PERFORMANCE'
  | 'TRAINING'
  | 'DOCUMENTS'
  | 'ANNOUNCEMENTS'
  | 'TASKS'
  | 'SETTINGS';

export interface WorkspaceRecord {
  id: number;
  module: WorkspaceModule;
  title: string;
  description: string | null;
  status: string;
  employeeId: number | null;
  employeeName: string | null;
  dueDate: string | null;
  score: number | null;
  visibleToEmployees: boolean;
  fileName: string | null;
  contentType: string | null;
  fileSize: number;
  createdAt: string;
  updatedAt: string;
}

export interface WorkspacePayload {
  title: string;
  description: string;
  status: string;
  employeeId: number | null;
  dueDate: string | null;
  score: number | null;
  visibleToEmployees: boolean;
}

export interface PayrollMonthTotal {
  period: string;
  total: number;
}

export interface DashboardSummary {
  employeeCount: number;
  currentPayrollTotal: number;
  payrollPeriod: string;
  attendanceTotalToday: number;
  attendancePresentToday: number;
  attendanceLateToday: number;
  attendanceAbsentToday: number;
  pendingLeaveCount: number;
  openJobCount: number;
  openTaskCount: number;
  publishedAnnouncementCount: number;
  payrollHistory: PayrollMonthTotal[];
}

@Injectable({ providedIn: 'root' })
export class WorkspaceService {
  private readonly http = inject(HttpClient);
  private readonly endpoint = `${API_BASE_URL}/workspace`;

  list(
    module: WorkspaceModule,
    filters: Record<string, string> = {},
  ): Observable<WorkspaceRecord[]> {
    return this.http.get<WorkspaceRecord[]>(`${this.endpoint}/${module}`, { params: filters });
  }

  create(module: WorkspaceModule, payload: WorkspacePayload): Observable<WorkspaceRecord> {
    return this.http.post<WorkspaceRecord>(`${this.endpoint}/${module}`, payload);
  }

  uploadDocument(payload: WorkspacePayload, file: File): Observable<WorkspaceRecord> {
    const formData = new FormData();
    formData.append('metadata', new Blob([JSON.stringify(payload)], { type: 'application/json' }));
    formData.append('file', file, file.name);
    return this.http.post<WorkspaceRecord>(`${this.endpoint}/DOCUMENTS/upload`, formData);
  }

  update(
    module: WorkspaceModule,
    id: number,
    payload: WorkspacePayload,
  ): Observable<WorkspaceRecord> {
    return this.http.put<WorkspaceRecord>(`${this.endpoint}/${module}/${id}`, payload);
  }

  updateStatus(
    module: WorkspaceModule,
    id: number,
    status: string,
  ): Observable<WorkspaceRecord> {
    return this.http.put<WorkspaceRecord>(`${this.endpoint}/${module}/${id}/status`, { status });
  }

  enrollTraining(id: number): Observable<WorkspaceRecord> {
    return this.http.post<WorkspaceRecord>(`${this.endpoint}/TRAINING/${id}/enroll`, {});
  }

  delete(module: WorkspaceModule, id: number): Observable<void> {
    return this.http.delete<void>(`${this.endpoint}/${module}/${id}`);
  }

  downloadDocument(id: number): Observable<Blob> {
    return this.http.get(`${this.endpoint}/DOCUMENTS/${id}/file`, { responseType: 'blob' });
  }

  dashboardSummary(): Observable<DashboardSummary> {
    return this.http.get<DashboardSummary>(`${API_BASE_URL}/dashboard/summary`);
  }
}
