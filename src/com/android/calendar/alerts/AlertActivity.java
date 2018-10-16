/*
 * Copyright (C) 2007 The Android Open Source Project
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

package com.android.calendar.alerts;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.NotificationManager;
import android.app.TaskStackBuilder;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.provider.CalendarContract.CalendarAlerts;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Button;
import android.widget.ListView;

import com.android.calendar.AsyncQueryService;
import com.android.calendar.EventInfoActivity;
import com.android.calendar.R;
import com.android.calendar.Utils;
import com.android.calendar.alerts.GlobalDismissManager.AlarmId;

import java.util.LinkedList;
import java.util.List;

/**
 * The alert panel that pops up when there is a calendar event alarm.
 * This activity is started by an intent that specifies an event id.
  */
public class AlertActivity extends Activity implements OnClickListener {
    private Button mDismissAllButton;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    void closeActivityIfEmpty() {
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onClick(View v) {
        if (v == mDismissAllButton) {
            NotificationManager nm =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            nm.cancelAll();

            finish();
        }
    }

    public boolean isEmpty() {
        return true;
    }

    public Cursor getItemForView(View view) {
        return null;
    }
}
