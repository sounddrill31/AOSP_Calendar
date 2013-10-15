package com.android.calendar;

import java.util.ArrayList;
import java.util.TimeZone;

import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;
import android.provider.CalendarContract.Events;
import android.provider.CalendarContract.ExtendedProperties;
import android.provider.CalendarContract.Reminders;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.text.format.Time;
import android.util.Log;

import com.android.calendar.EventInfoFragment.CalendarAccountInfo;
import com.android.calendar.EventInfoFragment.QueryHandler;
import com.android.calendar.event.EditEventHelper;

import com.motorola.calendarcommon.vcal.common.VCalConstants;

public class IcalInfoUtils {

    /**
     * Decoreate given uri with moto_visibility parameter. If an iCal file is being
     * previewed, the moto_visibility parameter will be set to false. Otherwise the
     * orginal uri will be returned.
     *
     * @param uri
     *        The content uri to be decoreated
     *
     * @return The decoreated uri
     */
    public static Uri decorateForScratch(Uri uri, boolean mIsIcsImport) {
        if (mIsIcsImport && (uri != null)) {
            Uri.Builder uriBuilder = uri.buildUpon();
            // TODO: consolidate the string constants
            uriBuilder.appendQueryParameter("use_hidden_calendar", String.valueOf(true));
            uri = uriBuilder.build();
        }

        return uri;
    }

    /**
     * Move the event that's being previewed to the given calendar account.
     *
     * @param accountInfo
     *        The calendar account to which the event will be moved.
     */
    public static void moveEventInDB(CalendarAccountInfo accountInfo, Cursor mEventCursor,QueryHandler mHandler,long mEventId) {
        if ((mEventCursor != null) && mEventCursor.moveToFirst()) {
            if (accountInfo.mId == mEventCursor.getInt(EventInfoFragment.EVENT_INDEX_CALENDAR_ID)) {
                // Return directly if the event is already in the calendar account
                return;
            }
        } else {
            return;
        }

        ArrayList<ContentProviderOperation> operations = new ArrayList<ContentProviderOperation>(1);
        ContentValues values = new ContentValues(1);

        // Move the event to the new calendar account
        values.put(Events.CALENDAR_ID, Long.valueOf(accountInfo.mId));
        addUpdateOperation(operations,
                ContentUris.withAppendedId(Events.CONTENT_URI, Long.valueOf(mEventId)),
                values, null, null);

        try {
            mHandler.startBatch(mHandler.getNextToken(), null, CalendarContract.AUTHORITY, operations, 0);
        } catch (Exception e) {
            Log.e(EventInfoFragment.TAG, "", e);
        }
    }

    public static void addUpdateOperation(ArrayList<ContentProviderOperation> operations, Uri uri,
            ContentValues values, String selection, String[] selectionArgs) {
        ContentProviderOperation.Builder builder = ContentProviderOperation.newUpdate(uri);
        if (!TextUtils.isEmpty(selection)) {
            builder.withSelection(selection, selectionArgs);
        }
        builder.withValues(values);
        operations.add(builder.build());
    }

    public static void addDeleteOperation(ArrayList<ContentProviderOperation> operations, Uri uri,
            String selection, String[] selectionArgs) {
        ContentProviderOperation.Builder builder = ContentProviderOperation.newDelete(uri);
        if (!TextUtils.isEmpty(selection)) {
            builder.withSelection(selection, selectionArgs);
        }
        operations.add(builder.build());
    }

    /**
     * Replace the given event by using the one which is being previewed.
     *
     * @param accountInfo
     *        The calendar account in which the event with _ID value
     *        eventId belongs to.
     *
     * @param eventId
     *        The _ID value of the event which is to be replaced.
     */
    public static void replaceEventInDB(CalendarAccountInfo accountInfo, long eventId,
            long mEventId, Cursor mRemindersCursor, QueryHandler mHandler, Cursor mEventCursor,
            long mStartMillis, long mEndMillis) {
        if (eventId == mEventId) {
            // Return direclty if they're the same event
            return;
        }

        ArrayList<ContentProviderOperation> operations = new ArrayList<ContentProviderOperation>();

        // Prepare the event ContentValues to update the existing event
        ContentValues values = getContentValuesFromEvent(mEventCursor,mStartMillis,mEndMillis);
        if (values != null) {
            // Update the "calendar_id" field for the event
            values.put(Events.CALENDAR_ID, accountInfo.mId);

            // Update the "hasAlarm" field for the event
            if ((mRemindersCursor != null) && mRemindersCursor.moveToFirst()) {
                values.put(Events.HAS_ALARM, 1);
            } else {
                values.put(Events.HAS_ALARM, 0);
            }

            addUpdateOperation(operations,
                    ContentUris.withAppendedId(Events.CONTENT_URI, Long.valueOf(eventId)),
                    values, null, null);

            // Update Reminders on existing event. Don't touch Attendees and ExtendedProperties
            // since local claendar doesn't permit modifying them.
            // Delete existing Reminders first
            String selection = Reminders.EVENT_ID + " = " + Long.valueOf(eventId);
            addDeleteOperation(operations, Reminders.CONTENT_URI, selection, null);
            // Then add new Reminders
            ContentProviderOperation.Builder b;
            if ((mRemindersCursor != null) && mRemindersCursor.moveToFirst()) {
                do {
                    values.clear();
                    values.put(Reminders.MINUTES,
                            mRemindersCursor.getInt(EditEventHelper.REMINDERS_INDEX_MINUTES));
                    values.put(Reminders.METHOD,
                            mRemindersCursor.getInt(EditEventHelper.REMINDERS_INDEX_METHOD));
                    values.put(Reminders.EVENT_ID, eventId);
                    b = ContentProviderOperation.newInsert(Reminders.CONTENT_URI).withValues(values);
                    operations.add(b.build());
                } while(mRemindersCursor.moveToNext());
            }

            try {
                mHandler.startBatch(mHandler.getNextToken(), null, CalendarContract.AUTHORITY, operations, 0);
            } catch (Exception e) {
                Log.e(EventInfoFragment.TAG, "", e);
            }
        }
    }


    /**
     * Goes through the event cursor and fills in content values for saving. This
     * method will perform the initial collection of values from the cursor and
     * put them into a set of ContentValues. It performs some basic work such as
     * fixing the time on allDay events and choosing whether to use an rrule or
     * dtend.
     *
     * @return values
     */
    public static ContentValues getContentValuesFromEvent(Cursor mEventCursor,long mStartMillis,long mEndMillis) {
        if ((mEventCursor != null) && (mEventCursor.moveToFirst())) {
            String title = mEventCursor.getString(EventInfoFragment.EVENT_INDEX_TITLE);
            int allDayFlag = mEventCursor.getInt(EventInfoFragment.EVENT_INDEX_ALL_DAY);
            String location = mEventCursor.getString(EventInfoFragment.EVENT_INDEX_EVENT_LOCATION);
            String description = mEventCursor.getString(EventInfoFragment.EVENT_INDEX_DESCRIPTION);
            String rrule = mEventCursor.getString(EventInfoFragment.EVENT_INDEX_RRULE);
            String timezone = mEventCursor.getString(EventInfoFragment.EVENT_INDEX_EVENT_TIMEZONE);
            int hasAttendeeFlag = mEventCursor.getInt(EventInfoFragment.EVENT_INDEX_HAS_ATTENDEE_DATA);
            int accessLevel = mEventCursor.getInt(EventInfoFragment.EVENT_INDEX_ACCESS_LEVEL);
            long calendarId = mEventCursor.getLong(EventInfoFragment.EVENT_INDEX_CALENDAR_ID);
            int available = mEventCursor.getInt(EventInfoFragment.EVENT_INDEX_AVAILABILITY);

            if (timezone == null) {
                timezone = TimeZone.getDefault().getID();
            }
            Time startTime = new Time(timezone);
            Time endTime = new Time(timezone);

            startTime.set(mStartMillis);
            endTime.set(mEndMillis);

            ContentValues values = new ContentValues();

            long startMillis;
            long endMillis;
            if (allDayFlag != 0) {
                // Reset start and end time, ensure at least 1 day duration, and set
                // the timezone to UTC, as required for all-day events.
                timezone = Time.TIMEZONE_UTC;
                startTime.hour = 0;
                startTime.minute = 0;
                startTime.second = 0;
                startTime.timezone = timezone;
                startMillis = startTime.normalize(true);

                endTime.hour = 0;
                endTime.minute = 0;
                endTime.second = 0;
                endTime.timezone = timezone;
                endMillis = endTime.normalize(true);
                if (endMillis < startMillis + DateUtils.DAY_IN_MILLIS) {
                    endMillis = startMillis + DateUtils.DAY_IN_MILLIS;
                }
            } else {
                startMillis = startTime.toMillis(true);
                endMillis = endTime.toMillis(true);
            }

            values.put(Events.CALENDAR_ID, calendarId);
            values.put(Events.EVENT_TIMEZONE, timezone);
            values.put(Events.TITLE, title);
            values.put(Events.ALL_DAY, allDayFlag);
            values.put(Events.DTSTART, startMillis);
            values.put(Events.RRULE, rrule);
            if (!TextUtils.isEmpty(rrule)) {
                addRecurrenceRule(values, mEventCursor, mStartMillis, mEndMillis);
            } else {
                values.put(Events.DURATION, (String) null);
                values.put(Events.DTEND, endMillis);
            }
            if (description != null) {
                values.put(Events.DESCRIPTION, description);
            } else {
                values.put(Events.DESCRIPTION, (String) null);
            }
            if (location != null) {
                values.put(Events.EVENT_LOCATION, location.trim());
            } else {
                values.put(Events.EVENT_LOCATION, (String) null);
            }
            values.put(Events.AVAILABILITY, available);
            values.put(Events.HAS_ATTENDEE_DATA, hasAttendeeFlag);
            values.put(Events.ACCESS_LEVEL, accessLevel);

            return values;
        }

        return null;
    }

    // Adds an rRule and duration to a set of content values
    private static void addRecurrenceRule(ContentValues values,Cursor mEventCursor,long mStartMillis,long mEndMillis) {
        if ((mEventCursor != null) && (mEventCursor.moveToFirst())) {
            String rrule = mEventCursor.getString(EventInfoFragment.EVENT_INDEX_RRULE);
            boolean isAllday = mEventCursor.getInt(EventInfoFragment.EVENT_INDEX_ALL_DAY) != 0;
            String duration = mEventCursor.getString(EventInfoFragment.EVENT_INDEX_DURATION);

            values.put(Events.RRULE, rrule);
            long end = mEndMillis;
            long start = mStartMillis;

            if (end > start) {
                if (isAllday) {
                    // if it's all day compute the duration in days
                    long days = (end - start + DateUtils.DAY_IN_MILLIS - 1)
                        / DateUtils.DAY_IN_MILLIS;
                    duration = "P" + days + "D";
                } else {
                    // otherwise compute the duration in seconds
                    long seconds = (end - start) / DateUtils.SECOND_IN_MILLIS;
                    duration = "P" + seconds + "S";
                }
            } else if (TextUtils.isEmpty(duration)) {

                // If no good duration info exists assume the default
                if (isAllday) {
                    duration = "P1D";
                } else {
                    duration = "P3600S";
                }
            }
            // recurring events should have a duration and dtend set to null
            values.put(Events.DURATION, duration);
            values.put(Events.DTEND, (Long) null);
        }
    }


    /**
     * Get event ID by the given UID and calendar account ID.
     *
     * @param uid
     *        UID of the event to be queried
     *
     * @param calendarId
     *        Calendar account to be queried in
     *
     * @return The event _ID of the found record. or -1L if there's no such record.
     */
    public static long getEventIdByUID(Context mContext, String uid, long calendarId) {
        ContentResolver resolver = mContext.getContentResolver();
        String selection = ExtendedProperties.NAME + "=? AND " +
            ExtendedProperties.VALUE + "=?";
        String selectionArgs[] = new String[] {VCalConstants.UID, uid};
        long eventId = -1L;

        Cursor c = resolver.query(ExtendedProperties.CONTENT_URI,
                new String[] {ExtendedProperties.EVENT_ID},
                selection, selectionArgs, null);
        if (c != null) {
            try {
                selection = Events._ID + "=? AND " +
                    Events.CALENDAR_ID + "=" + Long.toString(calendarId);
                while (c.moveToNext()) {
                    Cursor eventCursor = resolver.query(Events.CONTENT_URI,
                            new String[]{Events._ID}/* projection */,
                            selection /* selection */,
                            new String[]{c.getString(0)}/*selectionArgs*/,
                            null);
                    if (eventCursor != null) {
                        try {
                            if (eventCursor.moveToFirst()) {
                                eventId = eventCursor.getLong(0);
                            }
                        } finally {
                            eventCursor.close();
                        }
                    }
                    if (eventId != -1L) break;
                }
            } finally {
                c.close();
            }
        }

        return eventId;
    }
}
