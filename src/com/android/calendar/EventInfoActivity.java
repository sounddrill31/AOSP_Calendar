/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.calendar;

import static android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME;
import static android.provider.CalendarContract.EXTRA_EVENT_END_TIME;
import static android.provider.CalendarContract.Attendees.ATTENDEE_STATUS;

import android.app.ActionBar;
import android.app.Activity;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.CalendarContract;
import android.provider.CalendarContract.Attendees;
import android.util.Log;

//Begin iCal feature
import com.motorola.calendar.utils.IcalConfig;
import com.android.calendar.ics.IcsConstants;
//End iCal feature
import android.widget.Toast;

import com.android.calendar.CalendarEventModel.ReminderEntry;

import java.util.ArrayList;
import java.util.List;

public class EventInfoActivity extends Activity {
//        implements CalendarController.EventHandler, SearchView.OnQueryTextListener,
//        SearchView.OnCloseListener {

    private static final String TAG = "EventInfoActivity";
    private EventInfoFragment mInfoFragment;
    private long mStartMillis, mEndMillis;
    private long mEventId;
    // Begin iCal feature
    private boolean mIsIcsImport;
    private boolean mIsDialog;
    private int mAttendeeResponse;
    // End iCal feature

    // Create an observer so that we can update the views whenever a
    // Calendar event changes.
    private final ContentObserver mObserver = new ContentObserver(new Handler()) {
        @Override
        public boolean deliverSelfNotifications() {
            return false;
        }

        @Override
        public void onChange(boolean selfChange) {
            if (selfChange) return;
            if (mInfoFragment != null) {
                mInfoFragment.reloadEvents();
            }
        }
    };

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        // Get the info needed for the fragment
        Intent intent = getIntent();
        int attendeeResponse = 0;
        mEventId = -1;
        boolean isDialog = false;
        ArrayList<ReminderEntry> reminders = null;

        // Begin iCal feature
        mIsIcsImport = false;
        mIsDialog = false;
        mAttendeeResponse = Attendees.ATTENDEE_STATUS_NONE;
        // End iCal feature

        if (icicle != null) {
            mEventId = icicle.getLong(EventInfoFragment.BUNDLE_KEY_EVENT_ID);
            mStartMillis = icicle.getLong(EventInfoFragment.BUNDLE_KEY_START_MILLIS);
            mEndMillis = icicle.getLong(EventInfoFragment.BUNDLE_KEY_END_MILLIS);
            attendeeResponse = icicle.getInt(EventInfoFragment.BUNDLE_KEY_ATTENDEE_RESPONSE);
            isDialog = icicle.getBoolean(EventInfoFragment.BUNDLE_KEY_IS_DIALOG);

            reminders = Utils.readRemindersFromBundle(icicle);

            // Begin iCal feature
            mIsIcsImport = icicle.getBoolean(EventInfoFragment.BUNDLE_KEY_IS_ICS_IMPORT);
            mIsDialog = isDialog;
            mAttendeeResponse = attendeeResponse;
            // End iCal feature
        } else if (intent != null && Intent.ACTION_VIEW.equals(intent.getAction())) {
            mStartMillis = intent.getLongExtra(EXTRA_EVENT_BEGIN_TIME, 0);
            mEndMillis = intent.getLongExtra(EXTRA_EVENT_END_TIME, 0);
            // Begin iCal feature
            mAttendeeResponse = intent.getIntExtra(ATTENDEE_STATUS,
                    Attendees.ATTENDEE_STATUS_NONE);
            mIsIcsImport = intent.getBooleanExtra(IcsConstants.EXTRA_IMPORT_ICS, false);
            // End iCal feature
            Uri data = intent.getData();
            if (data != null) {
                try {
                    List<String> pathSegments = data.getPathSegments();
                    int size = pathSegments.size();
                    if (size > 2 && "EventTime".equals(pathSegments.get(2))) {
                        // Support non-standard VIEW intent format:
                        //dat = content://com.android.calendar/events/[id]/EventTime/[start]/[end]
                        mEventId = Long.parseLong(pathSegments.get(1));
                        if (size > 4) {
                            mStartMillis = Long.parseLong(pathSegments.get(3));
                            mEndMillis = Long.parseLong(pathSegments.get(4));
                        }
                    } else {
                        mEventId = Long.parseLong(data.getLastPathSegment());
                    }
                } catch (NumberFormatException e) {
                    if (mEventId == -1) {
                        // do nothing here , deal with it later
                    } else if (mStartMillis == 0 || mEndMillis ==0) {
                        // Parsing failed on the start or end time , make sure the times were not
                        // pulled from the intent's extras and reset them.
                        mStartMillis = 0;
                        mEndMillis = 0;
                    }
                }
            }
        }

        if (mEventId == -1) {
            Log.w(TAG, "No event id");
            Toast.makeText(this, R.string.event_not_found, Toast.LENGTH_SHORT).show();
            finish();
        }
        // Begin iCal feature
        if(mIsIcsImport && !IcalConfig.isICalFeatureEnabled()){
            Toast.makeText(this, R.string.ical_not_supported, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }else{
        // End iCal feature
        // If we do not support showing full screen event info in this configuration,
        // close the activity and show the event in AllInOne.
        Resources res = getResources();
        if (!res.getBoolean(R.bool.agenda_show_event_info_full_screen)
                && !res.getBoolean(R.bool.show_event_info_full_screen)) {
            CalendarController.getInstance(this)
                    .launchViewEvent(mEventId, mStartMillis, mEndMillis, mAttendeeResponse);//iCal feature
            finish();
            return;
        }
        setContentView(R.layout.simple_frame_layout);

        // Get the fragment if exists
        mInfoFragment = (EventInfoFragment)
                getFragmentManager().findFragmentById(R.id.main_frame);


        // Remove the application title
        ActionBar bar = getActionBar();
        if (bar != null) {
            bar.setDisplayOptions(ActionBar.DISPLAY_HOME_AS_UP | ActionBar.DISPLAY_SHOW_HOME);
        }

        // Create a new fragment if none exists
        if (mInfoFragment == null) {
            FragmentManager fragmentManager = getFragmentManager();
            FragmentTransaction ft = fragmentManager.beginTransaction();
            // Begin iCal feature
            Bundle extras = new Bundle();
            extras.putBoolean(IcsConstants.EXTRA_IMPORT_ICS, mIsIcsImport);
            // End iCal feature
            mInfoFragment = new EventInfoFragment(this, mEventId, mStartMillis, mEndMillis,
                    mAttendeeResponse, mIsDialog, (mIsDialog ?// iCal feature
                            EventInfoFragment.DIALOG_WINDOW_STYLE :
                                EventInfoFragment.FULL_WINDOW_STYLE),
                    reminders, extras);// iCal feature
            ft.replace(R.id.main_frame, mInfoFragment);
            ft.commit();
        }
    }
    }// iCal feature

    @Override
    protected void onNewIntent(Intent intent) {
        // From the Android Dev Guide: "It's important to note that when
        // onNewIntent(Intent) is called, the Activity has not been restarted,
        // so the getIntent() method will still return the Intent that was first
        // received with onCreate(). This is why setIntent(Intent) is called
        // inside onNewIntent(Intent) (just in case you call getIntent() at a
        // later time)."
        setIntent(intent);
    }


    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        // Begin iCal feature
        outState.putLong(EventInfoFragment.BUNDLE_KEY_EVENT_ID, mEventId);
        outState.putLong(EventInfoFragment.BUNDLE_KEY_START_MILLIS, mStartMillis);
        outState.putLong(EventInfoFragment.BUNDLE_KEY_END_MILLIS, mEndMillis);
        outState.putBoolean(EventInfoFragment.BUNDLE_KEY_IS_ICS_IMPORT, mIsIcsImport);
        outState.putBoolean(EventInfoFragment.BUNDLE_KEY_IS_DIALOG, mIsDialog);
        outState.putInt(EventInfoFragment.BUNDLE_KEY_ATTENDEE_RESPONSE, mAttendeeResponse);
        // End iCal feature
    }

    @Override
    protected void onResume() {
        super.onResume();
        getContentResolver().registerContentObserver(CalendarContract.Events.CONTENT_URI,
                true, mObserver);
    }

    @Override
    protected void onPause() {
        super.onPause();
        getContentResolver().unregisterContentObserver(mObserver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}
