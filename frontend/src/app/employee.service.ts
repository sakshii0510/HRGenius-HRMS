import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from './api-url';

export interface Employee {
  id: number;
  firstName: string;
  lastName: string;
  email: string;
  department: string | null;
  designation: string | null;
  salary: number | null;
  status: 'ACTIVE' | 'INACTIVE';
}

export type EmployeePayload = Omit<Employee, 'id'>;

@Injectable({ providedIn: 'root' })
export class EmployeeService {
  private readonly http = inject(HttpClient);
  private readonly endpoint = `${API_BASE_URL}/employees`;

  getAll(): Observable<Employee[]> {
    return this.http.get<Employee[]>(this.endpoint);
  }

  create(employee: EmployeePayload): Observable<Employee> {
    return this.http.post<Employee>(this.endpoint, employee);
  }

  update(id: number, employee: EmployeePayload): Observable<Employee> {
    return this.http.put<Employee>(`${this.endpoint}/${id}`, employee);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.endpoint}/${id}`);
  }
}
