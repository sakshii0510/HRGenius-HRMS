import { Injectable, signal } from '@angular/core';

export interface AccountPreferences {
  showTasks: boolean;
  showTraining: boolean;
  showAnnouncements: boolean;
}

const DEFAULT_PREFERENCES: AccountPreferences = {
  showTasks: true,
  showTraining: true,
  showAnnouncements: true,
};

@Injectable({ providedIn: 'root' })
export class AccountPreferencesService {
  readonly preferences = signal<AccountPreferences>({ ...DEFAULT_PREFERENCES });
  private currentEmail = '';

  loadFor(email: string): void {
    this.currentEmail = email.toLowerCase();
    const stored = localStorage.getItem(this.storageKey(this.currentEmail));
    if (!stored) {
      this.preferences.set({ ...DEFAULT_PREFERENCES });
      return;
    }
    try {
      const value: unknown = JSON.parse(stored);
      if (this.isPreferences(value)) {
        this.preferences.set(value);
        return;
      }
      console.warn('Saved account preferences have an invalid shape and will be reset.');
    } catch (error: unknown) {
      console.warn('Saved account preferences could not be read and will be reset.', error);
    }
    localStorage.removeItem(this.storageKey(this.currentEmail));
    this.preferences.set({ ...DEFAULT_PREFERENCES });
  }

  update(preferences: AccountPreferences): void {
    if (!this.currentEmail) {
      throw new Error('Sign in before changing account preferences.');
    }
    localStorage.setItem(this.storageKey(this.currentEmail), JSON.stringify(preferences));
    this.preferences.set({ ...preferences });
  }

  private storageKey(email: string): string {
    return `hrgenius.account.preferences.${email}`;
  }

  private isPreferences(value: unknown): value is AccountPreferences {
    if (typeof value !== 'object' || value === null) return false;
    const preferences = value as Partial<AccountPreferences>;
    return typeof preferences.showTasks === 'boolean' &&
      typeof preferences.showTraining === 'boolean' &&
      typeof preferences.showAnnouncements === 'boolean';
  }
}
