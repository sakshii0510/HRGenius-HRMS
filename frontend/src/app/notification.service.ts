import { Injectable, inject, signal, computed } from '@angular/core';
import { HrModulesService } from './hr-modules.service';
import { WorkspaceService } from './workspace.service';
import { AuthService } from './auth.service';

export interface AppNotification {
  id: string;
  type: 'sick_leave' | 'task_done' | 'leave_review' | 'payroll';
  title: string;
  message: string;
  time: string;
  read: boolean;
  icon: string;
}

const NOTIF_STORAGE_KEY = 'hrgenius.notifications.seen';

@Injectable({ providedIn: 'root' })
export class NotificationService {
  private readonly hrApi = inject(HrModulesService);
  private readonly workspaceApi = inject(WorkspaceService);
  private readonly auth = inject(AuthService);

  readonly notifications = signal<AppNotification[]>([]);
  readonly showPanel = signal(false);

  readonly unreadCount = computed(() =>
    this.notifications().filter((n) => !n.read).length,
  );

  private seenIds = new Set<string>(this.loadSeenIds());

  /** Load role-specific notifications */
  load(): void {
    const isAdmin = this.auth.currentUser()?.role === 'ADMIN';
    this.notifications.set([]);

    if (isAdmin) {
      this.loadAdminNotifications();
    } else {
      this.loadEmployeeNotifications();
    }
  }

  // ── ADMIN: Sick leaves (pending) + Completed tasks ────────────────────────
  private loadAdminNotifications(): void {
    // 1) Sick leave requests from employees (pending only)
    this.hrApi.getLeaveApplications().subscribe({
      next: (leaves) => {
        const sickNotifs = leaves
          .filter((l) => l.leaveType === 'SICK' && l.status === 'PENDING')
          .map((l) => {
            const id = `sick-${l.id}`;
            return {
              id,
              type: 'sick_leave' as const,
              title: `${l.employeeName} is on sick leave`,
              message: `${l.startDate} \u2192 ${l.endDate} \u00b7 Awaiting your review`,
              time: l.appliedAt,
              read: this.seenIds.has(id),
              icon: '\ud83e\udd12',
            };
          });
        this.mergeNotifications(sickNotifs);
      },
    });

    // 2) Completed tasks (recent ones)
    this.workspaceApi.list('TASKS').subscribe({
      next: (tasks) => {
        const doneNotifs = tasks
          .filter((t) => t.status === 'COMPLETED')
          .slice(0, 10)
          .map((t) => {
            const id = `task-done-${t.id}`;
            return {
              id,
              type: 'task_done' as const,
              title: `Task completed`,
              message: `${t.title}${t.employeeName ? ' \u00b7 by ' + t.employeeName : ''}`,
              time: t.updatedAt,
              read: this.seenIds.has(id),
              icon: '\u2713',
            };
          });
        this.mergeNotifications(doneNotifs);
      },
    });
  }

  // ── EMPLOYEE: Approved/Rejected leaves + New payroll records ─────────────
  private loadEmployeeNotifications(): void {
    // 1) Leave approved or rejected
    this.hrApi.getLeaveApplications().subscribe({
      next: (leaves) => {
        const leaveNotifs = leaves
          .filter((l) => (l.status === 'APPROVED' || l.status === 'REJECTED') && l.reviewedAt)
          .map((l) => {
            const id = `leave-review-${l.id}`;
            const approved = l.status === 'APPROVED';
            return {
              id,
              type: 'leave_review' as const,
              title: approved ? 'Leave approved \u2714' : 'Leave rejected',
              message: `${l.leaveType.charAt(0) + l.leaveType.slice(1).toLowerCase()} leave \u00b7 ${l.startDate} \u2192 ${l.endDate}`,
              time: l.reviewedAt ?? l.appliedAt,
              read: this.seenIds.has(id),
              icon: approved ? '\u2714' : '\u2715',
            };
          });
        this.mergeNotifications(leaveNotifs);
      },
    });

    // 2) New payroll records (salary credited)
    this.hrApi.getPayroll().subscribe({
      next: (records) => {
        const payrollNotifs = records
          .filter((p) => p.paymentStatus === 'PAID')
          .slice(0, 6)
          .map((p) => {
            const id = `payroll-${p.id}`;
            return {
              id,
              type: 'payroll' as const,
              title: 'Salary credited \ud83d\udcb0',
              message: `${p.salaryPeriod} \u00b7 Net: $${p.netSalary.toLocaleString()}`,
              time: p.salaryPeriod + '-01',
              read: this.seenIds.has(id),
              icon: '\ud83d\udcb0',
            };
          });
        this.mergeNotifications(payrollNotifs);
      },
    });
  }

  markAllRead(): void {
    const updated = this.notifications().map((n) => ({ ...n, read: true }));
    this.notifications.set(updated);
    this.saveSeenIds(updated.map((n) => n.id));
  }

  markRead(id: string): void {
    this.notifications.update((list) =>
      list.map((n) => (n.id === id ? { ...n, read: true } : n)),
    );
    this.seenIds.add(id);
    this.saveSeenIds([...this.seenIds]);
  }

  togglePanel(): void {
    this.showPanel.update((v) => !v);
  }

  closePanel(): void {
    this.showPanel.set(false);
  }

  private mergeNotifications(incoming: AppNotification[]): void {
    this.notifications.update((existing) => {
      const existingIds = new Set(existing.map((n) => n.id));
      const merged = [...existing];
      for (const n of incoming) {
        if (!existingIds.has(n.id)) merged.push(n);
      }
      merged.sort((a, b) => b.time.localeCompare(a.time));
      return merged.slice(0, 30);
    });
  }

  private loadSeenIds(): string[] {
    try {
      return JSON.parse(localStorage.getItem(NOTIF_STORAGE_KEY) ?? '[]') as string[];
    } catch {
      return [];
    }
  }

  private saveSeenIds(ids: string[]): void {
    this.seenIds = new Set(ids);
    localStorage.setItem(NOTIF_STORAGE_KEY, JSON.stringify(ids));
  }
}

