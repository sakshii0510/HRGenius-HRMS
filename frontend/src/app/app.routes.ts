import { Routes } from '@angular/router';
import { authGuard, guestGuard } from './auth.guard';

const loadDashboard = () => import('./dashboard').then((module) => module.DashboardComponent);

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
  { path: 'login', loadComponent: loadDashboard, canActivate: [guestGuard], data: { authPage: 'login' } },
  { path: 'signup', loadComponent: loadDashboard, canActivate: [guestGuard], data: { authPage: 'signup' } },
  { path: 'forgot-password', loadComponent: loadDashboard, canActivate: [guestGuard], data: { authPage: 'forgot' } },
  { path: 'dashboard', loadComponent: loadDashboard, canActivate: [authGuard], data: { page: 'dashboard' } },
  { path: 'employees', loadComponent: loadDashboard, canActivate: [authGuard], data: { page: 'employees' } },
  { path: 'onboarding', loadComponent: loadDashboard, canActivate: [authGuard], data: { page: 'onboarding' } },
  { path: 'performance', loadComponent: loadDashboard, canActivate: [authGuard], data: { page: 'performance' } },
  { path: 'recruitment', loadComponent: loadDashboard, canActivate: [authGuard], data: { page: 'recruitment' } },
  { path: 'training', loadComponent: loadDashboard, canActivate: [authGuard], data: { page: 'training' } },
  { path: 'attendance', loadComponent: loadDashboard, canActivate: [authGuard], data: { page: 'attendance' } },
  { path: 'leave', loadComponent: loadDashboard, canActivate: [authGuard], data: { page: 'leave' } },
  { path: 'payroll', loadComponent: loadDashboard, canActivate: [authGuard], data: { page: 'payroll' } },
  { path: 'documents', loadComponent: loadDashboard, canActivate: [authGuard], data: { page: 'documents' } },
  { path: 'announcements', loadComponent: loadDashboard, canActivate: [authGuard], data: { page: 'announcements' } },
  { path: 'analytics', loadComponent: loadDashboard, canActivate: [authGuard], data: { page: 'analytics' } },
  { path: 'tasks', loadComponent: loadDashboard, canActivate: [authGuard], data: { page: 'tasks' } },
  {
    path: 'settings',
    loadComponent: loadDashboard,
    canActivate: [authGuard],
    data: { page: 'settings' },
  },
  { path: '**', redirectTo: 'dashboard' },
];
