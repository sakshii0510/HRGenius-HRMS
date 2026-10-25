import { AccountPreferencesService } from './account-preferences.service';

describe('AccountPreferencesService', () => {
  beforeEach(() => localStorage.clear());

  it('persists dashboard preferences for the signed-in account', () => {
    const preferences = new AccountPreferencesService();
    preferences.loadFor('employee@example.com');
    preferences.update({
      showTasks: false,
      showTraining: true,
      showAnnouncements: false,
    });

    const restored = new AccountPreferencesService();
    restored.loadFor('EMPLOYEE@example.com');

    expect(restored.preferences()).toEqual({
      showTasks: false,
      showTraining: true,
      showAnnouncements: false,
    });
  });

  it('keeps preferences separate between accounts', () => {
    const preferences = new AccountPreferencesService();
    preferences.loadFor('first@example.com');
    preferences.update({
      showTasks: false,
      showTraining: false,
      showAnnouncements: true,
    });
    preferences.loadFor('second@example.com');

    expect(preferences.preferences()).toEqual({
      showTasks: true,
      showTraining: true,
      showAnnouncements: true,
    });
  });
});
