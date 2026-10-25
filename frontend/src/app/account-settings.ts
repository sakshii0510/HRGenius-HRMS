import { CommonModule } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AccountPreferences, AccountPreferencesService } from './account-preferences.service';
import { AuthService } from './auth.service';

@Component({
  selector: 'app-account-settings',
  imports: [CommonModule, FormsModule],
  templateUrl: './account-settings.html',
  styleUrl: './account-settings.css',
})
export class AccountSettingsComponent implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  readonly preferenceStore = inject(AccountPreferencesService);
  readonly user = this.auth.currentUser;
  readonly preferences = this.preferenceStore.preferences;
  readonly error = signal('');
  readonly saved = signal(false);

  ngOnInit(): void {
    const email = this.user()?.email;
    if (email) this.preferenceStore.loadFor(email);
  }

  updatePreference(key: keyof AccountPreferences, checked: boolean): void {
    this.error.set('');
    this.saved.set(false);
    try {
      this.preferenceStore.update({ ...this.preferences(), [key]: checked });
      this.saved.set(true);
    } catch (error: unknown) {
      console.error('Unable to save account preferences in this browser.', error);
      this.error.set('Your preference could not be saved on this device. Check browser storage settings and try again.');
    }
  }

  signOut(): void {
    this.auth.logout();
    void this.router.navigate(['/login']);
  }
}
