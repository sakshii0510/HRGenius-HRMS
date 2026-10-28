import { Injectable, inject, signal, computed } from '@angular/core';
import { HrModulesService } from './hr-modules.service';
import { WorkspaceService } from './workspace.service';
import { AuthService } from './auth.service';

export interface AppNotification {
  id: string;
  type: 'sick_leave' | 'task_done' | 'leave_review' | 'payroll' | 'attendance' | 'leave_pending' | 'interview' | 'announcement';
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

  /** Per-section unread counts for sidebar nav badges */
  readonly leaveUnread = computed(() =>
    this.notifications().filter(
      (n) => !n.read && (n.type === 'sick_leave' || n.type === 'leave_pending' || n.type === 'leave_review'),
    ).length,
  );

  readonly taskUnread = computed(() =>
    this.notifications().filter((n) => !n.read && n.type === 'task_done').length,
  );

  readonly attendanceUnread = computed(() =>
    this.notifications().filter((n) => !n.read && n.type === 'attendance').length,
  );

  readonly recruitmentUnread = computed(() =>
    this.notifications().filter((n) => !n.read && n.type === 'interview').length,
  );

  private seenIds = new Set<string>(this.loadSeenIds());

  load(): void {
    const isAdmin = this.auth.currentUser()?.role === 'ADMIN';
    this.notifications.set([]);
    if (isAdmin) {
      this.loadAdminNotifications();
    } else {
      this.loadEmployeeNotifications();
    }
  }

  private loadAdminNotifications(): void {
    this.hrApi.getLeaveApplications().subscribe({
      next: (leaves) => {
        const leaveNotifs = leaves
          .filter((l) => l.status === 'PENDING')
          .map((l) => {
            const id = `leave-pending-${l.id}`;
            const isSick = l.leaveType === 'SICK';
            return {
              id,
              type: (isSick ? 'sick_leave' : 'leave_pending') as AppNotification['type'],
              title: isSick
                ? `${l.employeeName} - sick leave request`
                : `${l.employeeName} applied for ${l.leaveType.charAt(0) + l.leaveType.slice(1).toLowerCase()} leave`,
              message: `${l.startDate} to ${l.endDate} - Awaiting your review`,
              time: l.appliedAt,
              read: this.seenIds.has(id),
              icon: isSick ? '🤒' : '📋',
            };
          });
        this.mergeNotifications(leaveNotifs);
      },
    });


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
              message: `${t.title}${t.employeeName ? ' · by ' + t.employeeName : ''}`,
              time: t.updatedAt,
              read: this.seenIds.has(id),
              icon: '✓',
            };
          });
        this.mergeNotifications(doneNotifs);
      },
    });
  }

  private loadEmployeeNotifications(): void {
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
              title: approved ? 'Leave approved ✔' : 'Leave rejected',
              message: `${l.leaveType.charAt(0) + l.leaveType.slice(1).toLowerCase()} leave · ${l.startDate} to ${l.endDate}`,
              time: l.reviewedAt ?? l.appliedAt,
              read: this.seenIds.has(id),
              icon: approved ? '✔' : '✕',
            };
          });
        this.mergeNotifications(leaveNotifs);
      },
    });

    this.hrApi.getApplications().subscribe({
      next: (applications) => {
        const interviewNotifs = applications
          .filter((a) => a.status === 'INTERVIEW' || a.status === 'OFFERED' || a.status === 'REJECTED')
          .slice(0, 10)
          .map((a) => {
            const id = `application-${a.id}-${a.status}`;
            const titleMap: Record<string, string> = {
              INTERVIEW: 'Interview scheduled',
              OFFERED: 'Job offer received 🎉',
              REJECTED: 'Application not selected',
            };
            return {
              id,
              type: 'interview' as const,
              title: titleMap[a.status] ?? a.status,
              message: a.jobTitle,
              time: a.appliedAt,
              read: this.seenIds.has(id),
              icon: a.status === 'OFFERED' ? '🎉' : a.status === 'INTERVIEW' ? '🗓' : '✕',
            };
          });
        this.mergeNotifications(interviewNotifs);
      },
    });

    this.workspaceApi.list('TASKS').subscribe({
      next: (tasks) => {
        const taskNotifs = tasks
          .filter((t) => t.status === 'COMPLETED')
          .slice(0, 8)
          .map((t) => {
            const id = `task-done-${t.id}`;
            return {
              id,
              type: 'task_done' as const,
              title: 'Task completed ✓',
              message: t.title,
              time: t.updatedAt,
              read: this.seenIds.has(id),
              icon: '✓',
            };
          });
        this.mergeNotifications(taskNotifs);
      },
    });

    this.workspaceApi.list('ANNOUNCEMENTS').subscribe({
      next: (records) => {
        const announcementNotifs = records
          .slice(0, 6)
          .map((a) => {
            const id = `announcement-${a.id}`;
            return {
              id,
              type: 'announcement' as const,
              title: a.title,
              message: a.description || 'New company announcement',
              time: a.updatedAt,
              read: this.seenIds.has(id),
              icon: '◉',
            };
          });
        this.mergeNotifications(announcementNotifs);
      },
    });

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
              title: 'Salary credited 💰',
              message: `${p.salaryPeriod} · Net: $${p.netSalary.toLocaleString()}`,
              time: p.salaryPeriod + '-01',
              read: this.seenIds.has(id),
              icon: '💰',
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
      return merged.slice(0, 50);
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

