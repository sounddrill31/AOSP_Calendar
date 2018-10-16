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

package com.android.calendar.event;

import android.content.ContentProviderOperation;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.CalendarContract.Attendees;
import android.provider.CalendarContract.Calendars;
import android.provider.CalendarContract.Colors;
import android.provider.CalendarContract.Events;
import android.provider.CalendarContract.Reminders;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.text.format.Time;
import android.text.util.Rfc822Token;
import android.text.util.Rfc822Tokenizer;
import android.util.Log;
import android.view.View;

import com.android.calendar.AbstractCalendarActivity;
import com.android.calendar.AsyncQueryService;
import com.android.calendar.CalendarEventModel;
import com.android.calendar.CalendarEventModel.Attendee;
import com.android.calendar.CalendarEventModel.ReminderEntry;
import com.android.calendar.Utils;
import com.android.calendarcommon2.DateException;
import com.android.calendarcommon2.EventRecurrence;
import com.android.calendarcommon2.RecurrenceProcessor;
import com.android.calendarcommon2.RecurrenceSet;
import com.android.common.Rfc822Validator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.TimeZone;

public class EditEventHelper {
    private static final String TAG = "EditEventHelper";

    private static final boolean DEBUG = false;

    // Used for parsing rrules for special cases.
    private EventRecurrence mEventRecurrence = new EventRecurrence();

    private static final String NO_EVENT_COLOR = "";

    public static final String[] EVENT_PROJECTION = new String[] {
            Events._ID, // 0
            Events.TITLE, // 1
            Events.DESCRIPTION, // 2
            Events.EVENT_LOCATION, // 3
            Events.ALL_DAY, // 4
            Events.HAS_ALARM, // 5
            Events.CALENDAR_ID, // 6
            Events.DTSTART, // 7
            Events.DTEND, // 8
            Events.DURATION, // 9
            Events.EVENT_TIMEZONE, // 10
            Events.RRULE, // 11
            Events._SYNC_ID, // 12
            Events.AVAILABILITY, // 13
            Events.ACCESS_LEVEL, // 14
            Events.OWNER_ACCOUNT, // 15
            Events.HAS_ATTENDEE_DATA, // 16
            Events.ORIGINAL_SYNC_ID, // 17
            Events.ORGANIZER, // 18
            Events.GUESTS_CAN_MODIFY, // 19
            Events.ORIGINAL_ID, // 20
            Events.STATUS, // 21
            Events.CALENDAR_COLOR, // 22
            Events.EVENT_COLOR, // 23
            Events.EVENT_COLOR_KEY // 24
    };
    protected static final int EVENT_INDEX_ID = 0;
    protected static final int EVENT_INDEX_TITLE = 1;
    protected static final int EVENT_INDEX_DESCRIPTION = 2;
    protected static final int EVENT_INDEX_EVENT_LOCATION = 3;
    protected static final int EVENT_INDEX_ALL_DAY = 4;
    protected static final int EVENT_INDEX_HAS_ALARM = 5;
    protected static final int EVENT_INDEX_CALENDAR_ID = 6;
    protected static final int EVENT_INDEX_DTSTART = 7;
    protected static final int EVENT_INDEX_DTEND = 8;
    protected static final int EVENT_INDEX_DURATION = 9;
    protected static final int EVENT_INDEX_TIMEZONE = 10;
    protected static final int EVENT_INDEX_RRULE = 11;
    protected static final int EVENT_INDEX_SYNC_ID = 12;
    protected static final int EVENT_INDEX_AVAILABILITY = 13;
    protected static final int EVENT_INDEX_ACCESS_LEVEL = 14;
    protected static final int EVENT_INDEX_OWNER_ACCOUNT = 15;
    protected static final int EVENT_INDEX_HAS_ATTENDEE_DATA = 16;
    protected static final int EVENT_INDEX_ORIGINAL_SYNC_ID = 17;
    protected static final int EVENT_INDEX_ORGANIZER = 18;
    protected static final int EVENT_INDEX_GUESTS_CAN_MODIFY = 19;
    protected static final int EVENT_INDEX_ORIGINAL_ID = 20;
    protected static final int EVENT_INDEX_EVENT_STATUS = 21;
    protected static final int EVENT_INDEX_CALENDAR_COLOR = 22;
    protected static final int EVENT_INDEX_EVENT_COLOR = 23;
    protected static final int EVENT_INDEX_EVENT_COLOR_KEY = 24;

    public static final String[] REMINDERS_PROJECTION = new String[] {
            Reminders._ID, // 0
            Reminders.MINUTES, // 1
            Reminders.METHOD, // 2
    };
    public static final int REMINDERS_INDEX_MINUTES = 1;
    public static final int REMINDERS_INDEX_METHOD = 2;
    public static final String REMINDERS_WHERE = Reminders.EVENT_ID + "=?";

    // Visible for testing
    static final String ATTENDEES_DELETE_PREFIX = Attendees.EVENT_ID + "=? AND "
            + Attendees.ATTENDEE_EMAIL + " IN (";

    public static final int DOES_NOT_REPEAT = 0;
    public static final int REPEATS_DAILY = 1;
    public static final int REPEATS_EVERY_WEEKDAY = 2;
    public static final int REPEATS_WEEKLY_ON_DAY = 3;
    public static final int REPEATS_MONTHLY_ON_DAY_COUNT = 4;
    public static final int REPEATS_MONTHLY_ON_DAY = 5;
    public static final int REPEATS_YEARLY = 6;
    public static final int REPEATS_CUSTOM = 7;

    protected static final int MODIFY_UNINITIALIZED = 0;
    protected static final int MODIFY_SELECTED = 1;
    protected static final int MODIFY_ALL_FOLLOWING = 2;
    protected static final int MODIFY_ALL = 3;

    protected static final int DAY_IN_SECONDS = 24 * 60 * 60;

    private final AsyncQueryService mService;

    // This allows us to flag the event if something is wrong with it, right now
    // if an uri is provided for an event that doesn't exist in the db.
    protected boolean mEventOk = true;

    public static final int ATTENDEE_ID_NONE = -1;
    public static final int[] ATTENDEE_VALUES = {
        Attendees.ATTENDEE_STATUS_NONE,
        Attendees.ATTENDEE_STATUS_ACCEPTED,
        Attendees.ATTENDEE_STATUS_TENTATIVE,
        Attendees.ATTENDEE_STATUS_DECLINED,
    };

    /**
     * This is the symbolic name for the key used to pass in the boolean for
     * creating all-day events that is part of the extra data of the intent.
     * This is used only for creating new events and is set to true if the
     * default for the new event should be an all-day event.
     */
    public static final String EVENT_ALL_DAY = "allDay";

    static final String[] CALENDARS_PROJECTION = new String[] {
            Calendars._ID, // 0
            Calendars.CALENDAR_DISPLAY_NAME, // 1
            Calendars.OWNER_ACCOUNT, // 2
            Calendars.CALENDAR_COLOR, // 3
            Calendars.CAN_ORGANIZER_RESPOND, // 4
            Calendars.CALENDAR_ACCESS_LEVEL, // 5
            Calendars.VISIBLE, // 6
            Calendars.MAX_REMINDERS, // 7
            Calendars.ALLOWED_REMINDERS, // 8
            Calendars.ALLOWED_ATTENDEE_TYPES, // 9
            Calendars.ALLOWED_AVAILABILITY, // 10
            Calendars.ACCOUNT_NAME, // 11
            Calendars.ACCOUNT_TYPE, //12
    };
    static final int CALENDARS_INDEX_ID = 0;
    static final int CALENDARS_INDEX_DISPLAY_NAME = 1;
    static final int CALENDARS_INDEX_OWNER_ACCOUNT = 2;
    static final int CALENDARS_INDEX_COLOR = 3;
    static final int CALENDARS_INDEX_CAN_ORGANIZER_RESPOND = 4;
    static final int CALENDARS_INDEX_ACCESS_LEVEL = 5;
    static final int CALENDARS_INDEX_VISIBLE = 6;
    static final int CALENDARS_INDEX_MAX_REMINDERS = 7;
    static final int CALENDARS_INDEX_ALLOWED_REMINDERS = 8;
    static final int CALENDARS_INDEX_ALLOWED_ATTENDEE_TYPES = 9;
    static final int CALENDARS_INDEX_ALLOWED_AVAILABILITY = 10;
    static final int CALENDARS_INDEX_ACCOUNT_NAME = 11;
    static final int CALENDARS_INDEX_ACCOUNT_TYPE = 12;

    static final String CALENDARS_WHERE_WRITEABLE_VISIBLE = Calendars.CALENDAR_ACCESS_LEVEL + ">="
            + Calendars.CAL_ACCESS_CONTRIBUTOR + " AND " + Calendars.VISIBLE + "=1";

    static final String CALENDARS_WHERE = Calendars._ID + "=?";

    static final String[] COLORS_PROJECTION = new String[] {
        Colors._ID, // 0
        Colors.ACCOUNT_NAME,
        Colors.ACCOUNT_TYPE,
        Colors.COLOR, // 1
        Colors.COLOR_KEY // 2
    };

    static final String COLORS_WHERE = Colors.ACCOUNT_NAME + "=? AND " + Colors.ACCOUNT_TYPE +
        "=? AND " + Colors.COLOR_TYPE + "=" + Colors.TYPE_EVENT;

    static final int COLORS_INDEX_ACCOUNT_NAME = 1;
    static final int COLORS_INDEX_ACCOUNT_TYPE = 2;
    static final int COLORS_INDEX_COLOR = 3;
    static final int COLORS_INDEX_COLOR_KEY = 4;

    static final String[] ATTENDEES_PROJECTION = new String[] {
            Attendees._ID, // 0
            Attendees.ATTENDEE_NAME, // 1
            Attendees.ATTENDEE_EMAIL, // 2
            Attendees.ATTENDEE_RELATIONSHIP, // 3
            Attendees.ATTENDEE_STATUS, // 4
    };
    static final int ATTENDEES_INDEX_ID = 0;
    static final int ATTENDEES_INDEX_NAME = 1;
    static final int ATTENDEES_INDEX_EMAIL = 2;
    static final int ATTENDEES_INDEX_RELATIONSHIP = 3;
    static final int ATTENDEES_INDEX_STATUS = 4;
    static final String ATTENDEES_WHERE = Attendees.EVENT_ID + "=? AND attendeeEmail IS NOT NULL";

    public static class AttendeeItem {
        public boolean mRemoved;
        public Attendee mAttendee;
        public Drawable mBadge;
        public int mUpdateCounts;
        public View mView;
        public Uri mContactLookupUri;

        public AttendeeItem(Attendee attendee, Drawable badge) {
            mAttendee = attendee;
            mBadge = badge;
        }
    }

    public EditEventHelper(Context context) {
        mService = ((AbstractCalendarActivity)context).getAsyncQueryService();
    }

    public EditEventHelper(Context context, CalendarEventModel model) {
        this(context);
        // TODO: Remove unnecessary constructor.
    }

    /**
     * Saves the event. Returns true if the event was successfully saved, false
     * otherwise.
     *
     * @param model The event model to save
     * @param originalModel A model of the original event if it exists
     * @param modifyWhich For recurring events which type of series modification to use
     * @return true if the event was successfully queued for saving
     */
    public boolean saveEvent(CalendarEventModel model, CalendarEventModel originalModel,
            int modifyWhich) {
        return true;
    }

    public static LinkedHashSet<Rfc822Token> getAddressesFromList(String list,
            Rfc822Validator validator) {
        LinkedHashSet<Rfc822Token> addresses = new LinkedHashSet<Rfc822Token>();
        Rfc822Tokenizer.tokenize(list, addresses);
        return addresses;
    }

    /**
     * When we aren't given an explicit start time, we default to the next
     * upcoming half hour. So, for example, 5:01 -> 5:30, 5:30 -> 6:00, etc.
     *
     * @return a UTC time in milliseconds representing the next upcoming half
     * hour
     */
    protected long constructDefaultStartTime(long now) {
        Time defaultStart = new Time();
        defaultStart.set(now);
        defaultStart.second = 0;
        defaultStart.minute = 30;
        long defaultStartMillis = defaultStart.toMillis(false);
        if (now < defaultStartMillis) {
            return defaultStartMillis;
        } else {
            return defaultStartMillis + 30 * DateUtils.MINUTE_IN_MILLIS;
        }
    }

    /**
     * When we aren't given an explicit end time, we default to an hour after
     * the start time.
     * @param startTime the start time
     * @return a default end time
     */
    protected long constructDefaultEndTime(long startTime) {
        return startTime + DateUtils.HOUR_IN_MILLIS;
    }

    // TODO think about how useful this is. Probably check if our event has
    // changed early on and either update all or nothing. Should still do the if
    // MODIFY_ALL bit.
    void checkTimeDependentFields(CalendarEventModel originalModel, CalendarEventModel model,
            ContentValues values, int modifyWhich) {
    }

    /**
     * Prepares an update to the original event so it stops where the new series
     * begins. When we update 'this and all following' events we need to change
     * the original event to end before a new series starts. This creates an
     * update to the old event's rrule to do that.
     *<p>
     * If the event's recurrence rule has a COUNT, we also need to reduce the count in the
     * RRULE for the exception event.
     *
     * @param ops The list of operations to add the update to
     * @param originalModel The original event that we're updating
     * @param endTimeMillis The time before which the event must end (i.e. the start time of the
     *        exception event instance).
     * @return A replacement exception recurrence rule.
     */
    public String updatePastEvents(ArrayList<ContentProviderOperation> ops,
            CalendarEventModel originalModel, long endTimeMillis) {
        boolean origAllDay = originalModel.mAllDay;
        String origRrule = originalModel.mRrule;
        String newRrule = origRrule;
        return newRrule;
    }

    // It's the first event in the series if the start time before being
    // modified is the same as the original event's start time
    static boolean isFirstEventInSeries(CalendarEventModel model,
            CalendarEventModel originalModel) {
        return model.mOriginalStart == originalModel.mStart;
    }

    public static boolean canModifyEvent(CalendarEventModel model) {
        return false;
    }

    public static boolean canModifyCalendar(CalendarEventModel model) {
        return false;
    }

    public static boolean canAddReminders(CalendarEventModel model) {
        return false;
    }

    public static boolean canRespond(CalendarEventModel model) {
        return false;
    }
}
