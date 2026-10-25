import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { API_BASE_URL } from './api-url';

export type UserRole = 'ADMIN' | 'EMPLOYEE';

export interface AuthenticatedUser {
  id: number;
  name: string;
  email: string;
  role: UserRole;
}

export interface AuthResponse {
  accessToken: string;
  tokenType: 'Bearer';
  expiresInSeconds: number;
  user: AuthenticatedUser;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface SignupRequest extends LoginRequest {
  name: string;
}

interface StoredSession {
  accessToken: string;
  expiresAt: number;
  user: AuthenticatedUser;
}

const AUTH_ENDPOINT = `${API_BASE_URL}/auth`;
const SESSION_KEY = 'hrgenius.auth.session';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private session = this.restoreSession();
  readonly currentUser = signal<AuthenticatedUser | null>(this.session?.user ?? null);

  login(credentials: LoginRequest): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>(`${AUTH_ENDPOINT}/login`, credentials)
      .pipe(tap((response) => this.saveSession(response)));
  }

  signup(details: SignupRequest): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>(`${AUTH_ENDPOINT}/signup`, details)
      .pipe(tap((response) => this.saveSession(response)));
  }

  isAuthenticated(): boolean {
    return this.getAccessToken() !== null;
  }

  getAccessToken(): string | null {
    if (this.session && this.session.expiresAt > Date.now()) {
      return this.session.accessToken;
    }
    if (this.session) this.logout();
    return null;
  }

  logout(): void {
    this.session = null;
    this.currentUser.set(null);
    sessionStorage.removeItem(SESSION_KEY);
  }

  private saveSession(response: AuthResponse): void {
    if (
      !response.accessToken ||
      response.tokenType !== 'Bearer' ||
      !Number.isFinite(response.expiresInSeconds) ||
      response.expiresInSeconds <= 0
    ) {
      throw new Error('The authentication server returned an invalid session.');
    }
    const session: StoredSession = {
      accessToken: response.accessToken,
      expiresAt: Date.now() + response.expiresInSeconds * 1000,
      user: response.user,
    };
    sessionStorage.setItem(SESSION_KEY, JSON.stringify(session));
    this.session = session;
    this.currentUser.set(response.user);
  }

  private restoreSession(): StoredSession | null {
    const serialized = sessionStorage.getItem(SESSION_KEY);
    if (!serialized) return null;

    try {
      const session: unknown = JSON.parse(serialized);
      if (this.isStoredSession(session) && session.expiresAt > Date.now()) {
        return session;
      }
    } catch {
      sessionStorage.removeItem(SESSION_KEY);
      return null;
    }
    sessionStorage.removeItem(SESSION_KEY);
    return null;
  }

  private isStoredSession(value: unknown): value is StoredSession {
    if (typeof value !== 'object' || value === null) return false;
    const session = value as Partial<StoredSession>;
    const user = session.user;
    return (
      typeof session.accessToken === 'string' &&
      typeof session.expiresAt === 'number' &&
      typeof user === 'object' &&
      user !== null &&
      typeof user.id === 'number' &&
      typeof user.name === 'string' &&
      typeof user.email === 'string' &&
      (user.role === 'ADMIN' || user.role === 'EMPLOYEE')
    );
  }
}
