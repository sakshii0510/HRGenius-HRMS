import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from './api-url';

export type AttendanceStatus = 'PRESENT' | 'LATE' | 'HALF_DAY' | 'ABSENT' | 'ON_LEAVE';
export type PayrollPaymentStatus = 'PAID' | 'PENDING';
export type LeaveType =
  | 'ANNUAL'
  | 'SICK'
  | 'PERSONAL'
  | 'MATERNITY'
  | 'PATERNITY'
  | 'UNPAID'
  | 'OTHER';
export type LeaveStatus = 'PENDING' | 'APPROVED' | 'REJECTED';
export type JobStatus = 'OPEN' | 'CLOSED';
export type ApplicationStatus =
  | 'APPLIED'
  | 'SCREENING'
  | 'INTERVIEW'
  | 'OFFERED'
  | 'REJECTED'
  | 'WITHDRAWN';

export interface AttendanceRecord {
  id: number;
  employeeId: number;
  employeeName: string;
  attendanceDate: string;
  checkIn: string | null;
  checkOut: string | null;
  status: AttendanceStatus;
}

export interface AttendancePayload {
  employeeId: number;
  attendanceDate: string;
  checkIn: string | null;
  checkOut: string | null;
  status: AttendanceStatus;
}

export interface LeaveApplication {
  id: number;
  employeeId: number;
  employeeName: string;
  leaveType: LeaveType;
  startDate: string;
  endDate: string;
  reason: string;
  status: LeaveStatus;
  appliedAt: string;
  reviewedAt: string | null;
  reviewComment: string | null;
}

export interface LeavePayload {
  employeeId: number;
  leaveType: LeaveType;
  startDate: string;
  endDate: string;
  reason: string;
}

export interface PayrollRecord {
  id: number;
  employeeId: number;
  employeeName: string;
  salaryPeriod: string;
  basicSalary: number;
  allowances: number;
  deductions: number;
  netSalary: number;
  paymentStatus: PayrollPaymentStatus;
}

export interface PayrollPayload {
  employeeId: number;
  salaryPeriod: string;
  basicSalary: number;
  allowances: number;
  deductions: number;
  paymentStatus: PayrollPaymentStatus;
}

export interface JobOpening {
  id: number;
  title: string;
  department: string;
  location: string;
  employmentType: string;
  description: string;
  salaryMin: number | null;
  salaryMax: number | null;
  status: JobStatus;
  postedDate: string;
}

export interface JobPayload {
  title: string;
  department: string;
  location: string;
  employmentType: string;
  description: string;
  salaryMin: number | null;
  salaryMax: number | null;
  status: JobStatus;
}

export interface Candidate {
  id: number;
  name: string;
  email: string;
  phone: string | null;
}

export interface CandidatePayload {
  name: string;
  email: string;
  phone: string | null;
}

export interface JobApplication {
  id: number;
  jobOpeningId: number;
  jobTitle: string;
  candidateId: number;
  candidateName: string;
  candidateEmail: string;
  candidatePhone: string | null;
  status: ApplicationStatus;
  coverLetter: string | null;
  appliedAt: string;
}

export interface JobApplicationPayload {
  phone: string | null;
  coverLetter: string | null;
}

@Injectable({ providedIn: 'root' })
export class HrModulesService {
  private readonly http = inject(HttpClient);
  private readonly attendanceUrl = `${API_BASE_URL}/attendance`;
  private readonly leaveUrl = `${API_BASE_URL}/leave-applications`;
  private readonly payrollUrl = `${API_BASE_URL}/payroll`;
  private readonly recruitmentUrl = `${API_BASE_URL}/recruitment`;

  getAttendance(filters: Record<string, string> = {}): Observable<AttendanceRecord[]> {
    return this.http.get<AttendanceRecord[]>(this.attendanceUrl, { params: filters });
  }

  createAttendance(payload: AttendancePayload): Observable<AttendanceRecord> {
    return this.http.post<AttendanceRecord>(this.attendanceUrl, payload);
  }

  updateAttendance(id: number, payload: AttendancePayload): Observable<AttendanceRecord> {
    return this.http.put<AttendanceRecord>(`${this.attendanceUrl}/${id}`, payload);
  }

  deleteAttendance(id: number): Observable<void> {
    return this.http.delete<void>(`${this.attendanceUrl}/${id}`);
  }

  getLeaveApplications(filters: Record<string, string> = {}): Observable<LeaveApplication[]> {
    return this.http.get<LeaveApplication[]>(this.leaveUrl, { params: filters });
  }

  createLeave(payload: LeavePayload): Observable<LeaveApplication> {
    return this.http.post<LeaveApplication>(this.leaveUrl, payload);
  }

  updateLeave(id: number, payload: LeavePayload): Observable<LeaveApplication> {
    return this.http.put<LeaveApplication>(`${this.leaveUrl}/${id}`, payload);
  }

  deleteLeave(id: number): Observable<void> {
    return this.http.delete<void>(`${this.leaveUrl}/${id}`);
  }

  reviewLeave(
    id: number,
    status: Exclude<LeaveStatus, 'PENDING'>,
    reviewComment: string,
  ): Observable<LeaveApplication> {
    return this.http.post<LeaveApplication>(`${this.leaveUrl}/${id}/review`, {
      status,
      reviewComment,
    });
  }

  getPayroll(filters: Record<string, string> = {}): Observable<PayrollRecord[]> {
    return this.http.get<PayrollRecord[]>(this.payrollUrl, { params: filters });
  }

  createPayroll(payload: PayrollPayload): Observable<PayrollRecord> {
    return this.http.post<PayrollRecord>(this.payrollUrl, payload);
  }

  updatePayroll(id: number, payload: PayrollPayload): Observable<PayrollRecord> {
    return this.http.put<PayrollRecord>(`${this.payrollUrl}/${id}`, payload);
  }

  deletePayroll(id: number): Observable<void> {
    return this.http.delete<void>(`${this.payrollUrl}/${id}`);
  }

  getJobs(search = '', status?: JobStatus): Observable<JobOpening[]> {
    const params: Record<string, string> = {};
    if (search.trim()) params['search'] = search.trim();
    if (status) params['status'] = status;
    return this.http.get<JobOpening[]>(`${this.recruitmentUrl}/jobs`, { params });
  }

  createJob(payload: JobPayload): Observable<JobOpening> {
    return this.http.post<JobOpening>(`${this.recruitmentUrl}/jobs`, payload);
  }

  updateJob(id: number, payload: JobPayload): Observable<JobOpening> {
    return this.http.put<JobOpening>(`${this.recruitmentUrl}/jobs/${id}`, payload);
  }

  deleteJob(id: number): Observable<void> {
    return this.http.delete<void>(`${this.recruitmentUrl}/jobs/${id}`);
  }

  getCandidates(search = ''): Observable<Candidate[]> {
    const params: Record<string, string> = {};
    if (search.trim()) params['search'] = search.trim();
    return this.http.get<Candidate[]>(`${this.recruitmentUrl}/candidates`, { params });
  }

  createCandidate(payload: CandidatePayload): Observable<Candidate> {
    return this.http.post<Candidate>(`${this.recruitmentUrl}/candidates`, payload);
  }

  updateCandidate(id: number, payload: CandidatePayload): Observable<Candidate> {
    return this.http.put<Candidate>(`${this.recruitmentUrl}/candidates/${id}`, payload);
  }

  deleteCandidate(id: number): Observable<void> {
    return this.http.delete<void>(`${this.recruitmentUrl}/candidates/${id}`);
  }

  getApplications(filters: Record<string, string> = {}): Observable<JobApplication[]> {
    return this.http.get<JobApplication[]>(`${this.recruitmentUrl}/applications`, {
      params: filters,
    });
  }

  applyForJob(jobId: number, payload: JobApplicationPayload): Observable<JobApplication> {
    return this.http.post<JobApplication>(
      `${this.recruitmentUrl}/jobs/${jobId}/applications`,
      payload,
    );
  }

  updateApplicationStatus(id: number, status: ApplicationStatus): Observable<JobApplication> {
    return this.http.put<JobApplication>(`${this.recruitmentUrl}/applications/${id}/status`, {
      status,
    });
  }

  withdrawApplication(id: number): Observable<void> {
    return this.http.delete<void>(`${this.recruitmentUrl}/applications/${id}`);
  }
}
