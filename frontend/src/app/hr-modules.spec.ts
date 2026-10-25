import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { AuthService } from './auth.service';
import { EmployeeService } from './employee.service';
import { HrModulesComponent } from './hr-modules';
import { HrModulesService } from './hr-modules.service';

describe('HrModulesComponent payroll helpers', () => {
  let requestedApplicationFilters: Record<string, string>;

  beforeEach(async () => {
    requestedApplicationFilters = {};
    await TestBed.configureTestingModule({
      imports: [HrModulesComponent],
      providers: [
        {
          provide: AuthService,
          useValue: {
            currentUser: signal({
              id: 1,
              name: 'Test Admin',
              email: 'admin@example.com',
              role: 'ADMIN',
            }),
          },
        },
        {
          provide: EmployeeService,
          useValue: {
            getAll: () => of([
              {
                id: 7,
                firstName: 'Taylor',
                lastName: 'Employee',
                email: 'taylor@example.com',
                department: 'People',
                designation: 'Partner',
                salary: 72000,
                status: 'ACTIVE',
              },
              {
                id: 8,
                firstName: 'Sam',
                lastName: 'NoSalary',
                email: 'sam@example.com',
                department: 'Finance',
                designation: 'Analyst',
                salary: null,
                status: 'ACTIVE',
              },
            ]),
          },
        },
        {
          provide: HrModulesService,
          useValue: {
            getPayroll: () => of([]),
            getJobs: () => of([]),
            getCandidates: () => of([]),
            getApplications: (filters: Record<string, string>) => {
              requestedApplicationFilters = filters;
              return of([]);
            },
          },
        },
      ],
    }).compileComponents();
  });

  it('prefills a new payroll basic salary from the selected employee annual salary', async () => {
    const fixture = TestBed.createComponent(HrModulesComponent);
    fixture.componentRef.setInput('page', 'payroll');
    fixture.detectChanges();
    await fixture.whenStable();

    fixture.componentInstance.startPayrollCreate();

    expect(fixture.componentInstance.payrollForm.employeeId).toBe(7);
    expect(fixture.componentInstance.payrollForm.basicSalary).toBe(6000);
    expect(fixture.componentInstance.payrollForm.paymentStatus).toBe('PENDING');
    expect(fixture.componentInstance.payrollSalaryHint()).toContain('annual salary');
    expect(fixture.componentInstance.departmentOptions()).toContain('People');
  });

  it('asks for a monthly salary when the selected employee profile has no salary', async () => {
    const fixture = TestBed.createComponent(HrModulesComponent);
    fixture.componentRef.setInput('page', 'payroll');
    fixture.detectChanges();
    await fixture.whenStable();

    fixture.componentInstance.selectPayrollEmployee(8);

    expect(fixture.componentInstance.payrollForm.basicSalary).toBe(0);
    expect(fixture.componentInstance.payrollSalaryHint()).toContain('No annual salary');
  });

  it('opens the applications list filtered to the selected job', async () => {
    const fixture = TestBed.createComponent(HrModulesComponent);
    fixture.componentRef.setInput('page', 'recruitment');
    fixture.detectChanges();
    await fixture.whenStable();

    fixture.componentInstance.showJobApplications({
      id: 42,
      title: 'People Partner',
      department: 'Human Resources',
      location: 'Remote',
      employmentType: 'Full-time',
      description: 'Support the people team.',
      salaryMin: 60000,
      salaryMax: 80000,
      status: 'OPEN',
      postedDate: '2026-10-01',
    });
    await fixture.whenStable();

    expect(requestedApplicationFilters).toEqual({ jobId: '42' });
  });

  it('preserves the payment status when editing payroll', async () => {
    const fixture = TestBed.createComponent(HrModulesComponent);
    fixture.componentRef.setInput('page', 'payroll');
    fixture.detectChanges();
    await fixture.whenStable();

    fixture.componentInstance.editPayroll({
      id: 9,
      employeeId: 7,
      employeeName: 'Taylor Employee',
      salaryPeriod: '2026-10',
      basicSalary: 6000,
      allowances: 250,
      deductions: 100,
      netSalary: 6150,
      paymentStatus: 'PAID',
    });

    expect(fixture.componentInstance.payrollForm.paymentStatus).toBe('PAID');
  });
});
