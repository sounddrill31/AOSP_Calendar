package com.android.calendar;

import android.app.backup.BackupAgentHelper;
import android.app.backup.SharedPreferencesBackupHelper;

public class CalendarBackupAgent extends BackupAgentHelper
{
    static final String SHARED_KEY = "shared_pref";

    public void onCreate () {
        addHelper(SHARED_KEY, new SharedPreferencesBackupHelper(this,
                                                                CalendarPreferenceActivity.SHARED_PREFS_NAME));
    }
}