/*
 * Copyright (C) 2010 Motorola, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 *
 * Modification History:
 **********************************************************
 * Date           Author       Comments
 * 02-Feb-2010    tbdc37       Ported from SyncML
 **********************************************************
 */

package com.motorola.calendarcommon.vcal.parser;


import com.motorola.calendarcommon.vcal.common.CalendarEvent;
import com.motorola.calendarcommon.vcal.common.Duration;
import com.motorola.calendarcommon.vcal.common.ICalNames;

import com.motorola.androidcommon.DateException;
import com.motorola.androidcommon.EventRecurrence;
import com.motorola.androidcommon.ICalendar.Component;
import com.motorola.androidcommon.ICalendar;
import com.motorola.androidcommon.ICalendar.FormatException;
import com.motorola.androidcommon.RecurrenceProcessor;
import com.motorola.androidcommon.RecurrenceSet;

import android.content.ContentValues;
import android.provider.CalendarContract;
import android.provider.CalendarContract.Events;

import android.text.TextUtils;
import android.text.format.Time;
import android.text.format.DateUtils;
import android.util.TimeFormatException;
import android.util.Log;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.TimeZone;
import java.util.Vector;

/**
 * Implements parser methods
 * @hide
 */
public class VCalParser_V2 extends VCalParser {
    private static final String TAG = "VCalParser_V2";
    private static final boolean DEBUG = false; // DON'T COMMIT IF THIS IS TRUE

    /**
     * A <TZID of VTIMEZONE, Java Timezone Id> map which stores the result of VTIMEZONE.
     */
    private Map<String, VTimeZone> mTzIdMap = new HashMap<String, VTimeZone>();

    /**
    * Constructor
    */
    public VCalParser_V2() {
    }
    /**
    * parses string based ical
    * @param strVcal vcal data
    * @return Collection of CalendarEvent
    */
    @Override
    public Collection<CalendarEvent> parse(final String strVcal)
        throws ICalendar.FormatException {
        return parseIcal(strVcal);
    }

    @Override
    public Collection<CalendarEvent> parse(ICalendar.Component iCalendar)
        throws ICalendar.FormatException {
        return parseIcal(iCalendar);
    }

   /**
    * parses string based ical
    * @param ical vcal data
    * @return Collection of CalendarEvent
    */
    private Collection<CalendarEvent> parseIcal(final String ical) throws ICalendar.FormatException {
        // Commented for now to use CalendarUtils APIs
         ICalendar.Component iCalendar = ICalendar.parseCalendar(ical);
         return parseIcal(iCalendar);
         // return null;
    }

    private Collection<CalendarEvent> parseIcal(ICalendar.Component iCalendar) throws ICalendar.FormatException {
        Vector<CalendarEvent> calendarEvents = new Vector<CalendarEvent>();
        List<ICalendar.Component> components = iCalendar.getComponents();

        if (components == null || components.size() == 0) {
            Log.w(TAG, "VCALENDAR is empty, having no sub-components!");
            return Collections.emptyList();
        }

        if (DEBUG) {
            Log.v(TAG, "Size of components:" + components.size());
        }

        // Clear the TimeZone ID map before processing current Timezone in ICS.
        mTzIdMap.clear();

        // We need to handle VTIMEZONE first.
        Iterator<ICalendar.Component> it = components.iterator();
        while (it.hasNext()) {
            ICalendar.Component c = it.next();
            if (ICalendar.Component.VTIMEZONE.equals(c.getName())) {
                processVTimeZone(c);
                it.remove();
            }
        }

        it = components.iterator();
        while (it.hasNext()) {
            ICalendar.Component c = it.next();
            String name = c.getName();
            if (DEBUG) {
                Log.v(TAG, "Component name:" + name);
            }
            if (!TextUtils.isEmpty(name)) {
                if (ICalendar.Component.VEVENT.equalsIgnoreCase(name)) {
                    CalendarEvent evt = processVEvent(c);
                    if (evt != null) {
                        calendarEvents.add(evt);
                        if (DEBUG) {
                            Log.v(TAG, "Parse VEvent = " + evt.toString());
                        }
                    }
                } else if (ICalendar.Component.VTODO.equalsIgnoreCase(name)) {
                    CalendarEvent vTodo = processVTodo(c);
                    if(vTodo != null) {
                        vTodo.isVtodo = true;
                        calendarEvents.add(vTodo);
                        if (DEBUG) {
                            Log.v(TAG, "Parse VTODO = " + vTodo.toString());
                        }
                    }
                } else {
                    Log.w(TAG, "Unsupported vCal component : " + name);
                }
            } else {
                if (DEBUG) {
                    Log.v(TAG, "Component name null");
                }
            }
        }
        return calendarEvents;
    }

    private static final int YEAR = 2010;
    private static final long MS_HALF_DAY = 1000 * 60 * 60 * 12;
    private void processVTimeZone(Component c) throws FormatException {
        final String COMP = ICalendar.Component.VTIMEZONE + ", ";

        VTimeZone vTz = new VTimeZone();

        ICalendar.Property tzIdProp = c.getFirstProperty(ICalNames.PROP_TZ_ID);
        String tzId = tzIdProp == null ? null : tzIdProp.getValue();
        if (TextUtils.isEmpty(tzId)) {
            throw new FormatException(ICalendar.Component.VTIMEZONE + " - Missing TZID");
        }
        vTz.mTzId = tzId;

        if (DEBUG) {
            Log.v(TAG, COMP + "TZID=" + tzId);
        }

        // One of 'standardc' or 'daylightc' MUST occur
        // and each MAY occur more than once.
        List<Component> tzComps = c.getComponents();
        if (tzComps == null || tzComps.size() == 0) {
            throw new FormatException(ICalendar.Component.VTIMEZONE + " - " + "Empty components");
        }

        long stdDateMillis = -1L, dstDateMillis = -1L;
        for (Component comp : tzComps) {
            VTimeZone.TzParam tzParam = processTzParam(comp);
            if (ICalNames.OBJ_NAME_TZ_STANDARD.equals(tzParam.name)) {
                // VTIMEZONE is complex, it can have more than one STANDARD to
                // define different parameter of timezone in different period.
                // But Java Timezone doesn't support such multiple standard
                // time, so we only respect the last Standard Time of Timezone,
                // that is to use the latest DTSTART included in STANDARD
                // component to find a Java Timezone.
                if (vTz.mStdParam != null && vTz.mStdParam.dtStart.after(tzParam.dtStart)) {
                    Log.v(TAG, COMP + tzParam.name + ", skip " + ICalNames.PROP_DATE_START + "="
                            + tzParam.dtStart.format2445());
                    continue;
                }
                vTz.mStdParam = tzParam;
            } else {
                // Only DAYLIGHT is possible, otherwise processTzParam will throw
                if (vTz.mDstParam != null && vTz.mDstParam.dtStart.after(tzParam.dtStart)) {
                    Log.v(TAG, COMP + tzParam.name + ", skip " + ICalNames.PROP_DATE_START + "="
                            + tzParam.dtStart.format2445());
                    continue;
                }
                vTz.mDstParam = tzParam;
            }
        }

        if (vTz.mStdParam == null) {
            throw new FormatException(ICalendar.Component.VTIMEZONE
                    + ", Only support when 'standardc' occurs.");
        }

        if (DEBUG) {
            Log.v(TAG, COMP + vTz.mStdParam.toString());
            if (vTz.mDstParam != null) {
                Log.v(TAG, COMP + vTz.mDstParam.toString());
            }
        }

        // Transition date of STANDARD
        if (!TextUtils.isEmpty(vTz.mStdParam.rrule)) {
            int year = YEAR;
            if (year < vTz.mStdParam.dtStart.year) {
                year = vTz.mStdParam.dtStart.year + 1;
            }
            stdDateMillis = getTimeFromTimezoneRRule(vTz.mStdParam.rrule, year);
            if (DEBUG) {
                if (stdDateMillis > 0) {
                    Time date = new Time(Time.TIMEZONE_UTC);
                    date.set(stdDateMillis);
                    Log.v(TAG, COMP + "standard date is " + date.format2445());
                }
            }
        }
        // Transition date of DAYLIGHT
        if (vTz.mDstParam != null && !TextUtils.isEmpty(vTz.mDstParam.rrule)) {
            int year = YEAR;
            if (year < vTz.mDstParam.dtStart.year) {
                year = vTz.mDstParam.dtStart.year + 1;
            }
            dstDateMillis = getTimeFromTimezoneRRule(vTz.mDstParam.rrule, year);
            if (DEBUG) {
                if (dstDateMillis > 0) {
                    Time date = new Time(Time.TIMEZONE_UTC);
                    date.set(dstDateMillis);
                    Log.v(TAG, COMP + "daylight date is " + date.format2445());
                }
            }
        }

        // Get all the available TimeZone IDs based from stdOffset
        String[] timezones = TimeZone.getAvailableIDs(vTz.mStdParam.offset);

        if (dstDateMillis == -1L) {
            List<TimeZone> tzCandidates = new LinkedList<TimeZone>();
            // Doesn't have a DST start date, so to lookup the timezones that
            // has NO DST
            for (String id : timezones) {
                TimeZone tz = TimeZone.getTimeZone(id);
                // No DST in this timezone, so we'll take it as a candidate.
                if (tz.getDSTSavings() == 0) {
                    tzCandidates.add(tz);
                }
            }

            // Get the best timezone from Candidates
            vTz.mJavaId = getBestTimezoneMatch(vTz.mTzId, tzCandidates);

        } else {
            long preDstMillis = dstDateMillis - MS_HALF_DAY;
            long postDstMillis = dstDateMillis + 3*MS_HALF_DAY;
            long preStdMillis = stdDateMillis - MS_HALF_DAY;
            long postStdMillis = stdDateMillis + 3*MS_HALF_DAY;

            List<TimeZone> tzCandidates = new LinkedList<TimeZone>();

            String temp0TzId = null;

            for (String id : timezones) {
                TimeZone tz = TimeZone.getTimeZone(id);
                int tzDstSaving = tz.getDSTSavings();
                if (vTz.mDstParam.offset - vTz.mStdParam.offset == tzDstSaving) {
                    int preDstDateOffset = tz.getOffset(preDstMillis);
                    int postDstDateOffset = tz.getOffset(postDstMillis);
                    int preStdDateOffset = tz.getOffset(preStdMillis);
                    int postStdDateOffset = tz.getOffset(postStdMillis);
                    //if (DEBUG) {
                    //    Log.v(TAG, COMP + " -- [" + preStdDateOffset + ", " + postStdDateOffset
                    //            + "] - [" + preDstDateOffset + ", " + postDstDateOffset + "]");
                    //}
                    if (preDstDateOffset == vTz.mStdParam.offset
                            && postDstDateOffset == vTz.mDstParam.offset
                            && preStdDateOffset == vTz.mDstParam.offset
                            && postStdDateOffset == vTz.mStdParam.offset) {
                        tzCandidates.add(tz);
                    }
                    if (temp0TzId == null) {
                        if (tz.getOffset(dstDateMillis) == vTz.mDstParam.offset) {
                            temp0TzId = id;
                        }
                    }
                } else {
                    if (DEBUG) {
                        Log.v(TAG, COMP + " -- " + tz.getID() + " DST Saving is " + tzDstSaving);
                    }
                }
            }
            // Get best timezone from candidates
            vTz.mJavaId = getBestTimezoneMatch(vTz.mTzId, tzCandidates);

            if (vTz.mJavaId == null) {
                Log.v(TAG, COMP + "Cannot find an exact match for timezone, using backups ...");
                if (temp0TzId != null) {
                    Log.v(TAG, COMP + "Using timezone with same standard offset & same offset on daylight date");
                    vTz.mJavaId = temp0TzId;
                }
            }
        }

        // when convert the event timezone to UTC, we should convert all its Date-Time to UTC.
        if (vTz.mJavaId == null) {
            if (DEBUG) {
                Log.v(TAG, COMP + "NO backup found, will use UTC as TimeZone!");
            }
        }

        if (DEBUG) {
            Log.v(TAG, COMP + "Mapping ['" + vTz.mTzId + "', '" + vTz.mJavaId + "']");
        }
        mTzIdMap.put(vTz.mTzId, vTz);
    }

    private static final int MINUTES_PER_HR = 60;
    private static final int SECONDS_PER_MIN = 60;
    private static final int MILLIS_PER_MIN = SECONDS_PER_MIN * 1000;

    private int convertOffsetToMillis(String value) {
        int sign;
        int index;
        if (value.charAt(0) == '-') {
            sign = -1;
            index = 1;
        } else if (value.charAt(0) == '+') {
            sign = 1;
            index = 1;
        } else {
            sign = 1;
            index = 0;
        }
        int hour = 0, minute = 0;
        if (value.length() - index >= 2) {
            hour = (value.charAt(index) - '0') * 10 + (value.charAt(index + 1) - '0');
        }
        if (value.length() - index >= 4) {
            minute = (value.charAt(index + 2) - '0') * 10 + (value.charAt(index + 3) - '0');
        }
        return sign * (hour * MINUTES_PER_HR + minute) * MILLIS_PER_MIN;
    }

    /**
     * Replace escape characters from the string
     */
    private String replaceEscapes(String line) {
        if (line == null) {
            return null;
        }
        char[] ch = line.toCharArray();
        int len = ch.length;
        StringBuilder sb = new StringBuilder();
        boolean escape = false;
        for (int i = 0; i < len; i++) {
            if (ch[i] == '\\') {
                escape = true;
                continue;
            }
            if (escape == true && ch[i] == 'n') {
                sb.append("\n");
            } else {
                sb.append(ch[i]);
            }
            escape = false;
        }
        return sb.toString();
    }

    private CalendarEvent processVTodo(ICalendar.Component c) throws ICalendar.FormatException {
        if (DEBUG) {
            Log.v(TAG, "Entering processVTodo");
        }

        ICalendar.Property prop = null;
        CalendarEvent event = new CalendarEvent();

        boolean allDay;
        prop = c.getFirstProperty("DTSTART");
        if (prop != null) {
            allDay = parseDateTimeProperty(prop, mTzIdMap, event);
        } else {
            allDay = false;
        }

        prop = c.getFirstProperty("DUE");
        if (prop != null) {
            allDay &= parseDateTimeProperty(prop, mTzIdMap, event);
        } else {
            allDay = false;
        }

        prop = c.getFirstProperty("COMPLETED");
        if (prop != null) {
            allDay &= parseDateTimeProperty(prop, mTzIdMap, event);
        } else {
            allDay = false;
        }

        event.isAllDay = allDay;

        parseEventProperties(c, event);

        return event;
    }

    /**
     * Parse the VEVENT component.
     *
     * @param c The VEVENT component.
     * @return The calendar event constructed from VEVENT.
     * @throws ICalendar.FormatException
     */
    private CalendarEvent processVEvent(Component c) throws ICalendar.FormatException {
        if (DEBUG) {
            Log.v(TAG, "processing VEVENT");
        }

        CalendarEvent event = new CalendarEvent();

        ICalendar.Property prop;

        // DTSTART
        boolean allDay;
        prop = c.getFirstProperty("DTSTART");
        if (prop != null) {
            allDay = parseDateTimeProperty(prop, mTzIdMap, event);
        } else {
            allDay = false;
        }

        // DTEND
        prop = c.getFirstProperty("DTEND");
        if (prop != null) {
            allDay &= parseDateTimeProperty(prop, mTzIdMap, event);
        } else {
            allDay = false;
        }

        // Event is allDay if and only if both DTSTART and DTEND are allDay.
        event.isAllDay = allDay;

        parseEventProperties(c, event);

        // if there's no DTEND or DTEND is invalid, we'll try to caculate it by using DTSTART+DURATION
        if ((event.dtEnd < event.dtStart) && (event.duration != null)) {
            Duration duration = new Duration();
            try {
                duration.parse(event.duration);
                event.dtEnd = event.dtStart + duration.getMillis();
            } catch (DateException ex) {
                Log.e(TAG, "ERROR parsing the duration string >" + event.duration + "<");
            }
        }

        // Validate allDay event
        //
        // According to SDK API description for Time.parse, date-time string includes only date
        // and no time field (such as 20100224) will be treated as allDay. So event with DTSTART
        // 20100224T000000 and DTEND 20100225T000000 will not be treated as all day event according to
        // current algorithm (although it should be). We'll do some amendment for this case here.
        //
        if (!allDay) {
            long duration = event.dtEnd - event.dtStart;
            if (duration % DateUtils.DAY_IN_MILLIS == 0) {
                Time t = new Time(event.tzId);
                t.set(event.dtStart);
                if (t.hour == 0 && t.minute == 0 && t.second == 0) {
                    event.isAllDay = true;
                    // reset start time and end time and set time zone to UTC, so this event will be
                    // shown on the same day on different time zones. That's required by all-day event.
                    t.timezone = Time.TIMEZONE_UTC;
                    event.dtStart = t.normalize(true);

                    t.clear(event.tzId);
                    t.set(event.dtEnd);
                    t.timezone = Time.TIMEZONE_UTC;
                    event.dtEnd = t.normalize(true);

                    event.tzId = Time.TIMEZONE_UTC;
                }
            }
        }

        return event;
    }

    private void parseEventProperties(ICalendar.Component c, CalendarEvent event)
            throws ICalendar.FormatException {
        // Event summary
        ICalendar.Property prop = c.getFirstProperty("SUMMARY");
        if (prop != null) {
            event.summary = replaceEscapes(prop.getValue());
        }

        // Event description
        prop = c.getFirstProperty("DESCRIPTION");
        if (prop != null) {
            event.description = replaceEscapes(prop.getValue());
        }

        // Event Category
        prop = c.getFirstProperty("CATEGORIES");
        if (prop != null) {
            event.categories = replaceEscapes(prop.getValue());
        }

        // Event priority
        prop = c.getFirstProperty("PRIORITY");
        if (prop != null) {
            try {
                event.priority = Integer.parseInt(prop.getValue());
            } catch (Exception numberException) {
                // Undefined priority
                event.priority = 0;
            }
        }

        // Event status
        prop = c.getFirstProperty("STATUS");
        // TENTATIVE, CONFIRMED, CANCELLED. Default value is TENTATIVE
        if (prop != null) {
            String status = prop.getValue();
            status = status.toUpperCase();
            if (status.equals("TENTATIVE")) {
                event.status = CalendarContract.Events.STATUS_TENTATIVE;
            } else if (status.equals("CONFIRMED")) {
                event.status = CalendarContract.Events.STATUS_CONFIRMED;
            } else if (status.equals("CANCELLED")) {
                event.status = CalendarContract.Events.STATUS_CANCELED;
            } else {
                event.status = CalendarContract.Events.STATUS_TENTATIVE;
            }
        } else {
            event.status = CalendarContract.Events.STATUS_TENTATIVE;
        }

        // Busy bit - Transparency
        prop = c.getFirstProperty("TRANSP");
        event.isTransparent = false;
        if (prop != null) {
            String transp = prop.getValue();
            if (transp.equalsIgnoreCase("TRANSPARENT")) {
                event.isTransparent = true;
            }
        }

        // Event location
        prop = c.getFirstProperty("LOCATION");
        if (prop != null) {
            event.location = replaceEscapes(prop.getValue());
        }

        // Event duration text
        prop = c.getFirstProperty("DURATION");
        if (prop != null) {
            event.duration = prop.getValue();
        }

        // RRULE
        prop = c.getFirstProperty("RRULE");
        if (prop != null) {
            String rrule = prop.getValue();
            event.rrule = rrule;
        }

        // EXRULE
        prop = c.getFirstProperty("EXRULE");
        if (prop != null) {
            String exrule = prop.getValue();
            event.exrule = exrule;
        }

        // List<ICalendar.Property> rdateList = c.getProperties("RDATE");
        // if (rdateList != null) {
        // handleDateList(rdateList, event.rdates);
        // }

        // List<ICalendar.Property> exdateList = c.getProperties("EXDATE");
        // if (exdateList != null) {
        // handleDateList(exdateList, event.exdate);
        // }

        // ORGANIZER
        prop = c.getFirstProperty("ORGANIZER");
        if (prop != null) {
            event.organizer = handleAttendee(prop, event.attendees, true);
        }

        // ATTENDEE
        List<ICalendar.Property> attendeeList = c.getProperties("ATTENDEE");
        if (attendeeList != null) {
            Iterator<ICalendar.Property> i = attendeeList.iterator();
            while (i.hasNext()) {
                ICalendar.Property attendee = i.next();
                handleAttendee(attendee, event.attendees, false);
            }
        }

        // VALARM
        List<ICalendar.Component> subcomps = c.getComponents();
        if (subcomps != null) {
            for (ICalendar.Component subc : subcomps) {
                if (ICalendar.Component.VALARM.equals(subc.getName())) {
                    processVAlarm(subc, event);
                } else {
                    Log.v(TAG, "NOT parsing " + subc.getName() + " under " + c.getName());
                }
            }
        }

        // Recurrenct properties
        prop = c.getFirstProperty("RDATE");
        if (prop != null) {
            event.rdate = prop.getValue();
        }
        prop = c.getFirstProperty("EXDATE");
        if (prop != null) {
            event.exdate = prop.getValue();
        }
        if (!TextUtils.isEmpty(event.rrule)) {
            event.lastDate = event.dtEnd;
        }

        // UID
        prop = c.getFirstProperty("UID");
        if (prop != null) {
            event.uid = prop.getValue();
        }

        // Calculate the last date
        try {
            calculateLastDate(event);
        } catch(Exception e) {
            e.printStackTrace();
        }

        //Extended properties,
        handleExtendedProperties(c, event);

    }

    /**
     * Parse Date-Time in iCalendar. Event's tzId is set when parsing Date-Time value.
     *
     * @param prop The property contains the Date-Time
     * @param tzIdMap The timezone - timezone Java Id mapping, parsed from VTIMEZONE
     * @param event THe Calendar event.
     * @return True if the Date-Time is a all-day time.
     * @throws ICalendar.FormatException
     */
    private boolean parseDateTimeProperty(ICalendar.Property prop, Map<String, VTimeZone> tzIdMap,
            CalendarEvent event) throws ICalendar.FormatException {
        final String name = prop.getName();
        final boolean isTagDtStart = name.equals("DTSTART");
        final boolean isTagCompleted = name.equals("COMPLETED");

        final String date2445 = prop.getValue();
        ICalendar.Parameter tzIdParam = prop.getFirstParameter("TZID");
        if (tzIdParam != null) {
            // TZID exists
            String tzIdValue = removeDoubleQuotes(tzIdParam.value);
            if (DEBUG) {
                Log.v(TAG, prop.getName() + " - TZID >" + tzIdValue + "<");
            }
            String tzIdJava = null;
            VTimeZone vTz = tzIdMap.get(tzIdValue);
            if (vTz == null) {
                // When the TzId is not recognized, parse it as local time.
                if (DEBUG) {
                    Log.w(TAG, "TZID of " + name + " is NOT found, '" + tzIdValue + "'");
                }
                tzIdJava = TimeZone.getDefault().getID();
            } else if (vTz.mJavaId == null) {
                // Use UTC as the TimeZone, if the Java TzId is NOT known (parse failed)
                tzIdJava = Time.TIMEZONE_UTC;
            } else {
                tzIdJava = vTz.mJavaId;
            }

            Time t = new Time(tzIdJava);
            try {
                t.parse(date2445);
                // If VTimeZone is not recognized
                if (vTz != null && vTz.mJavaId == null) {
                    int offset;
                    long millis = t.toMillis(true);
                    if (vTz.mDstParam == null) {
                        // If no DAYLIGHT defined in VTIMEZONE, we assume that this TimeZone only
                        // has standard offset, not care if it has a rrule or not.
                        offset = vTz.mStdParam.offset;
                    } else {
                        long stdStartMillis = getTimeFromTimezoneRRule(vTz.mStdParam.rrule, t.year);
                        long dstStartMillis = getTimeFromTimezoneRRule(vTz.mDstParam.rrule, t.year);
                        if (dstStartMillis < stdStartMillis) { // Northern Hemisphere
                            if (millis >= dstStartMillis && millis < stdStartMillis) {
                                // DST offset
                                offset = vTz.mDstParam.offset;
                            } else {
                                // STD offset
                                offset = vTz.mStdParam.offset;
                            }
                        } else { // Southern Hemisphere
                            if (millis >= stdStartMillis && millis < dstStartMillis) {
                                // STD offset
                                offset = vTz.mStdParam.offset;
                            } else {
                                // DST offset
                                offset = vTz.mDstParam.offset;
                            }
                        }
                    }
                    millis = millis - offset;
                    t.set(millis);
                }
            } catch (TimeFormatException e) {
                throw new ICalendar.FormatException("Cannot parse time : " + date2445);
            }
            if (DEBUG) {
                Log.v(TAG, prop.getName() + " : " + date2445);
                Log.v(TAG, prop.getName() + "(Time) : " + t.toString());
            }
            if (isTagCompleted) {
                event.comlpetedDate = t.normalize(true);
            } else if (isTagDtStart) {
                event.dtStart = t.normalize(true);
            } else {
                event.dtEnd = t.normalize(true);
                event.due = event.dtEnd;
            }

            event.tzId = tzIdJava;

            return t.allDay;
        }

        // UTC or floating time
        return parseDateTimeValue(date2445, isTagDtStart, isTagCompleted, event);
    }

    private boolean parseDateTimeValue(String date2445, boolean isTagDtStart,
            boolean isTagCompleted, CalendarEvent event) throws ICalendar.FormatException {
        Time t;
        if (date2445.endsWith("Z") || date2445.endsWith("z")) {
            // UTC time
            t = new Time(Time.TIMEZONE_UTC);
        } else {
            // Floating time, local timezone.
            t = new Time(TimeZone.getDefault().getID());
        }

        try {
            t.parse(date2445);
        } catch (TimeFormatException e) {
            throw new ICalendar.FormatException("Cannot parse time :" + date2445);
        }
        if (t.allDay) {
            // IKSTABLEFOURV-4913 .ics preview time is different from event time
            // when get the all day meeting invite ics from a gmail, there's no
            // propertites like below:
            // DTSTART;TZID=China Standard Time:20110315T083035
            // DTEND;TZID=China Standard Time:20110315T093035
            // consequently, it maybe mistaken as a NOT all day event
            // however, t.parse(strRFC2445) can be used to check whether time is
            // date only
            // if so, we still take it as an all day event
            t.timezone = Time.TIMEZONE_UTC;
        }
        if (isTagCompleted) {
            event.comlpetedDate = t.normalize(true);
        } else if (isTagDtStart) {
            event.dtStart = t.normalize(true);
        } else {
            event.dtEnd = t.normalize(true);
            event.due = event.dtEnd;
        }

        event.tzId = t.timezone;

        return t.allDay;
    }

    /**
     * fetch attendees and convert to CalendarEvent attendees
     *
     * @param attendee
     * @param attendees
     * @param isOrganizer
     */
    private CalendarEvent.Attendee handleAttendee(ICalendar.Property attendee,
            Vector<CalendarEvent.Attendee> attendees, boolean isOrganizer) {

        CalendarEvent.Attendee att = new CalendarEvent.Attendee();
        ICalendar.Parameter parameter = attendee.getFirstParameter("CN");
        if (parameter != null) {
            att.name = removeDoubleQuotes(parameter.value);
        }

        parameter = attendee.getFirstParameter("ROLE");
        if (parameter != null) {
            String role = removeDoubleQuotes(parameter.value.toUpperCase());
            if (role.equals("REQ-PARTICIPANT")) {
                att.type = CalendarContract.Attendees.TYPE_REQUIRED;
            } else if (role.equals("OPT-PARTICIPANT")) {
                att.type = CalendarContract.Attendees.TYPE_OPTIONAL;
            } else {
                att.type = CalendarContract.Attendees.TYPE_NONE;
            }
        }

        att.relationship = isOrganizer ? CalendarContract.Attendees.RELATIONSHIP_ORGANIZER
            : CalendarContract.Attendees.RELATIONSHIP_ATTENDEE;
        if (isOrganizer) {
            att.status = CalendarContract.Attendees.ATTENDEE_STATUS_ACCEPTED;
        }

        parameter = attendee.getFirstParameter("PARTSTAT");
        if (parameter != null) {
            String partStat = removeDoubleQuotes(parameter.value.toUpperCase());
            if (partStat.equals("ACCEPTED")) {
                att.status = CalendarContract.Attendees.ATTENDEE_STATUS_ACCEPTED;
            } else if (partStat.equals("DECLINED")) {
                att.status = CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED;
            } else if (partStat.equals("TENTATIVE")) {
                att.status = CalendarContract.Attendees.ATTENDEE_STATUS_TENTATIVE;
            } else {
                att.status = CalendarContract.Attendees.ATTENDEE_STATUS_NONE;
            }
        }

        String email = removeDoubleQuotes(attendee.getValue());
        if (email.startsWith("mailto:") || email.startsWith("MAILTO:")) {
            int index = email.indexOf(":");
            email = email.substring(index + 1, email.length());
        }
        att.email = email;

        if (!isOrganizer) {
            for (CalendarEvent.Attendee a : attendees) {
                if (a.relationship == CalendarContract.Attendees.RELATIONSHIP_ORGANIZER) {
                    if (att.email.equalsIgnoreCase(a.email)) {
                        Log.v(TAG, "Dupliate attendee, email address same as organizer.");
                        return att;
                    }
                }
            }
        }

        attendees.add(att);

        return att;
    }

    /**
     * Process VALARM parsing.
     *
     * @param c The VALARM component.
     * @param e The Calendar Event.
     */
    private void processVAlarm(ICalendar.Component c, CalendarEvent e)
            throws ICalendar.FormatException {
        CalendarEvent.Alarm alarm = new CalendarEvent.Alarm();
        alarm.state = CalendarContract.CalendarAlerts.STATE_SCHEDULED;

        // Parse Action
        ICalendar.Property prop = c.getFirstProperty("ACTION");
        if (prop == null) {
            throw new FormatException("Expected " + ICalendar.Component.VALARM + " - ACTION property");
        }
        String action = prop.getValue();
        if ("DISPLAY".equals(action)) {
            alarm.method = CalendarContract.Reminders.METHOD_ALERT;
        } else if ("EMAIL".equals(action)) {
            alarm.method = CalendarContract.Reminders.METHOD_EMAIL;
        } else {
            alarm.method = CalendarContract.Reminders.METHOD_DEFAULT;
        }

        // Parse the TRIGGER
        prop = c.getFirstProperty("TRIGGER");
        if (prop == null) {
            throw new FormatException("Expected " + ICalendar.Component.VALARM + " - TRIGGER property");
        }
        ICalendar.Parameter valueParam = prop.getFirstParameter("VALUE");
        if (valueParam != null && "DATE-TIME".equals(valueParam.value)) {
            // Date-Time
            String absTime = prop.getValue();
            if (!TextUtils.isEmpty(absTime)) {
                handleAbsoluteTrigger(alarm, absTime, e.dtStart, e.dtEnd);
            }
        } else {
            // Duration
            boolean relStart = true;
            ICalendar.Parameter relParam = prop.getFirstParameter("RELATED");
            if (relParam != null) {
                String relValue = removeDoubleQuotes(relParam.value);
                if ("END".equals(relValue)) {
                    relStart = false;
                }
            }

            String durationStr = prop.getValue();
            if (durationStr != null) {
                Duration durationParser = new Duration();
                try {
                    durationParser.parse(durationStr);
                    long millis = durationParser.getMillis();
                    int minutes = (int) millis / (60 * 1000);
                    alarm.minutes = minutes;
                    alarm.alarmTime = (relStart ? e.dtStart : e.dtEnd) + millis;
                } catch (DateException ex) {
                    // Parse Exception in Duration
                    Log.e(TAG, "ERROR in parsing TRIGGER's duration >" + durationStr + "<", ex);
                    Log.w(TAG, "Using dtStart or dtEnd as alarm time");
                    alarm.minutes = 0;
                    if (e.dtStart != -1L) {
                        alarm.alarmTime = e.dtStart;
                    } else {
                        alarm.alarmTime = e.dtEnd;
                    }
                }
            } else {
                Log.w(TAG, "NULL duration text in TRIGGER");
            }
        }

        e.hasAlarm = true;
        e.alarms.add(alarm);
    }

    private void handleAbsoluteTrigger(CalendarEvent.Alarm alarm, final String absTime,
            final long dtStart, final long dtEnd) {
        Time t = new Time();
        t.parse(absTime);
        t.normalize(true);
        long millis = t.toMillis(true);
        alarm.alarmTime = millis;
        // TODO
        // Calculate Alarm.minutes
        long milliSec = 0L;
        if (dtStart != -1L) {
            milliSec = dtStart - millis;
        } else {
            // This situation will occur when the parsed data is
            // of todo type..it may not have start time.
            // So alarm will be calculated from due date
            if (DEBUG) {
                Log.v(TAG, "dtend : " + dtEnd);
                Log.v(TAG, "mills : " + millis);
            }
            milliSec = dtEnd - millis;
        }

        long minutes = milliSec / 60 / 1000;
        alarm.minutes = minutes;
    }

    /**
    * calculate last date of occurence
    * @param event
    */
    private void calculateLastDate(CalendarEvent event) throws Exception {
        ContentValues values = new ContentValues();
        values.put(Events.DTSTART, event.dtStart);
        values.put(Events.DTEND, event.dtEnd);
        values.put(Events.RRULE, event.rrule);
        values.put(Events.DURATION, event.duration);
        values.put(Events.EVENT_TIMEZONE, event.tzId);
        values.put(Events.RDATE, event.rdate);
        values.put(Events.EXRULE, event.exrule);
        values.put(Events.EXDATE, event.exdate);

        Long dtStart = values.getAsLong(Events.DTSTART);
        if (dtStart == null) {
            return;
        }
        long dtstartMillis = dtStart.longValue();
        long lastMillis = -1;

        // Can we use dtend with a repeating event?  What does that even
        // mean?
        // NOTE: if the repeating event has a dtend, we convert it to a
        // duration during event processing, so this situation should not
        // occur.
        Long dtEnd = values.getAsLong(Events.DTEND);
        if (dtEnd != null) {
            lastMillis = dtEnd.longValue();
        } else {
            // find out how long it is
            Duration duration = new Duration();
            String durationStr = values.getAsString(Events.DURATION);
            if (durationStr != null) {
                duration.parse(durationStr);
            }

            RecurrenceSet recur = new RecurrenceSet(values);

            if (recur.hasRecurrence()) {
                // the event is repeating, so find the last date it
                // could appear on

                String tz = values.getAsString(Events.EVENT_TIMEZONE);

                if (TextUtils.isEmpty(tz)) {
                    // floating timezone
                    tz = Time.TIMEZONE_UTC;
                }
                Time dtstartLocal = new Time(tz);

                dtstartLocal.set(dtstartMillis);

                RecurrenceProcessor rp = new RecurrenceProcessor();
                lastMillis = rp.getLastOccurence(dtstartLocal, recur);
                if (lastMillis == -1) {
                    //return lastMillis;  // -1
                }
            } else {
                // the event is not repeating, just use dtstartMillis
                lastMillis = dtstartMillis;
            }
            // that was the beginning of the event.  this is the end.
            lastMillis += duration.getMillis();
            if(lastMillis != -1) {
                event.lastDate = lastMillis;
            }
        }
    }

    /**
      * Removes leading and trailing double quotes from the text string.
      *
      * @param text The text string.
      * @return The text string after processing.
      */
     private static String removeDoubleQuotes(String text) {
         if (!TextUtils.isEmpty(text)) {
             int len = text.length();
             if (len >= 2 && text.startsWith("\"") && text.endsWith("\"")) {
                 text = String.copyValueOf(text.toCharArray(), 1, len - 2);
             }
             return text;
         }

         return text;
     }

     //
     // Timezone parsing utilities

     // Process the sub-component under VTIMEZONE, to get TimeZone parameters
     private VTimeZone.TzParam processTzParam(Component comp) throws FormatException {
         String compName = comp.getName();
         if (!(ICalNames.OBJ_NAME_TZ_STANDARD.equals(compName))
                 && !(ICalNames.OBJ_NAME_TZ_DAYLIGHT.equals(compName))) {
             throw new FormatException("Unexpected component under " + ICalendar.Component.VTIMEZONE
                     + " - " + compName);
         }

         VTimeZone.TzParam tzParam = new VTimeZone.TzParam();

         // The name
         tzParam.name = compName;

         // DTSTART
         ICalendar.Property dtStartProp = comp.getFirstProperty(ICalNames.PROP_DATE_START);
         String dtStartStr = dtStartProp == null ? null : dtStartProp.getValue();
         if (TextUtils.isEmpty(dtStartStr)) {
             throw new FormatException("Missing " + ICalNames.PROP_DATE_START + " in "
                     + comp.getName() + " part included in " + ICalendar.Component.VTIMEZONE);
         }

         // This dtStart is only a reference in time, so it doesn't care the TimeZone it self
         tzParam.dtStart = new Time();
         tzParam.dtStart.parse(dtStartStr);

         // Offset To
         ICalendar.Property tzOffsetTo = comp.getFirstProperty(ICalNames.PROP_TZ_OFFSET_TO);
         String offsetToStr = tzOffsetTo == null ? null : tzOffsetTo.getValue();
         if (TextUtils.isEmpty(offsetToStr)) {
             throw new FormatException("Missing " + ICalNames.PROP_TZ_OFFSET_TO + " in "
                     + comp.getName() + " part included in " + ICalendar.Component.VTIMEZONE);
         }
         tzParam.offsetStr = offsetToStr;
         tzParam.offset = convertOffsetToMillis(offsetToStr);

         // NOTE: TZOFFSETFROM is also REQUIRED, but as we don't use it,
         // so not did the check

         // rrule
         ICalendar.Property rRuleProp = comp.getFirstProperty(ICalNames.PROP_RECURRENCE_RULE);
         String rruleStr = rRuleProp == null ? null : rRuleProp.getValue();
         if (!TextUtils.isEmpty(rruleStr)) {
             tzParam.rrule = rruleStr;
         }

         return tzParam;
     }

     // Calculate a day in the specified year according to the RRULE, return the
     // time in milliseconds since epoch
     private long getTimeFromTimezoneRRule(String rRuleStr, int year) {
         if (TextUtils.isEmpty(rRuleStr)) {
             return -1L;
         }

         java.util.Calendar calendar = java.util.Calendar.getInstance(TimeZone.getTimeZone("UTC"));
         calendar.set(/* YEAR */year, /* MONTH */0, /* DAY_OF_MONTH */1, /* HOUR_OF_DAY */0,
                     /* MINUTE */0, /* SECOND */0);

         RecurrenceSet rSet = new RecurrenceSet(rRuleStr, null, null, null);
         EventRecurrence rRule = rSet.rrules[0];
         // TimeZone's RRULE is assuming to be YEARLY in practice.
         if (rRule.freq == EventRecurrence.YEARLY) {
             // Year Day
             if (rRule.byyeardayCount > 0) {
                 calendar.set(java.util.Calendar.DAY_OF_YEAR, rRule.byyearday[0]);
             }
             // Month
             if (rRule.bymonthCount > 0) {
                 calendar.set(java.util.Calendar.MONTH, rRule.bymonth[0] - 1);
             }
             // Month Day
             if (rRule.bymonthdayCount > 0) {
                 calendar.set(java.util.Calendar.DAY_OF_MONTH, rRule.bymonthday[0]);
             }
             // DayOfWeek, Number of Day occurs in Month
             if (rRule.bydayCount > 0) {
                 calendar.set(java.util.Calendar.DAY_OF_WEEK, EventRecurrence.day2CalendarDay(rRule.byday[0]));
                 calendar.set(java.util.Calendar.DAY_OF_WEEK_IN_MONTH, rRule.bydayNum[0]);
             }

             // NOTE: BYSETPOS, BYHOUR, BYMINUTE are NOT supported
             return calendar.getTimeInMillis();
         }

         Log.e(TAG, "Failed to parse RRULE in VTIMEZONE >" + rRuleStr + "<");
         return -1L;
     }

     //
     // Timezone name/id is not long usually, and in most time it will not exceed 4 words.
     // So if we have a name that matches 3 words, then we think it is good enough.
     //
     private static final int ACCURACY_GOOD = 75;
     private String getBestTimezoneMatch(String referenceTzId, List<TimeZone> tzCandidates) {
         final String COMP = ICalendar.Component.VTIMEZONE + ", ";
         // Check WAcc (Word Accuracy) of each candidate TimeZone's ID, Display Name,
         // v.s. the tzIdValue(Reference). Get a best TimeZone that has highest WAR.
         if (tzCandidates.size() > 1) {
             TimeZone tzBest = tzCandidates.remove(0);
             int maxAcc = getWAccOfTimezone(referenceTzId, tzBest);
             for (TimeZone tz : tzCandidates) {
                 if (maxAcc >= ACCURACY_GOOD) break; // already perfect!
                 int accTz = getWAccOfTimezone(referenceTzId, tz);
                 if (accTz > maxAcc) {
                     maxAcc = accTz;
                     tzBest = tz;
                 }
             }
             Log.v(TAG, COMP + "Best WAcc is >" + maxAcc + "<, of Timezone [" + tzBest.getID()
                     + ", " + tzBest.getDisplayName() + "]");
             return tzBest.getID();
         } else if (tzCandidates.size() == 1) {
             Log.v(TAG, COMP + "Only one match!");
             return tzCandidates.remove(0).getID();
         } else {
             Log.w(TAG, COMP + "No available Timezone matches!");
         }

         return null;
     }

     // Word Accuracy Rate is (Integer.MIN_VALUE, 100], where 100 means most accurate.
     /**
      * Get the accuracy for the given TimeZone compared to the reference. The algorithm
      * compares the reference with the ID and display name of given TimeZone. It calculate
      * the word accuracy based on the Position-Independent Word Error Rate.
      *
      * @param reference The reference timezone text.
      * @param tz The given TimeZone
      * @return The accuracy of the given TimeZone on the reference.
      */
     private int getWAccOfTimezone(String reference, TimeZone tz) {
         if (TextUtils.isEmpty(reference) || tz == null) {
             return Integer.MIN_VALUE;
         }
         StringTokenizer st = null;
         int i;

         // Reference sentence as array
         st = new StringTokenizer(reference, " /");
         String[] refArray = new String[st.countTokens()];
         i = 0;
         while (st.hasMoreTokens()) {
             refArray[i] = st.nextToken().toLowerCase();
             i++;
         }

         // TimeZone Id as array
         st = new StringTokenizer(tz.getID(), " /");
         String[] tzIdArray = new String[st.countTokens()];
         i = 0;
         while (st.hasMoreTokens()) {
             tzIdArray[i] = st.nextToken().toLowerCase();
             i++;
         }
         // Get accuracy on Timezone Id
         int waccTzId = getWordAccuracy(refArray, tzIdArray);

         // TimeZone Display Name as array
         st = new StringTokenizer(tz.getDisplayName(), " /");
         String[] tzNameArray = new String[st.countTokens()];
         i = 0;
         while (st.hasMoreTokens()) {
             tzNameArray[i] = st.nextToken().toLowerCase();
             i++;
         }
         // Get accuracy on Timezone Display Name
         int waccTzName = getWordAccuracy(refArray, tzNameArray);

         int waccTz = waccTzName >= waccTzId ? waccTzName : waccTzId;
         if (DEBUG) {
             Log.v(TAG, "Calculate '" + tz.getID() + "' Accuracy: >" + waccTz + "<");
         }

         return waccTz;
     }

     // Use Position-Independent Word Error Rate to calculate the accuracy
     private int getWordAccuracy(String[] referenceArray, String[] candidateArray) {
         LinkedList<String> reference = new LinkedList<String>(Arrays.asList(referenceArray));
         LinkedList<String> candidate = new LinkedList<String>(Arrays.asList(candidateArray));

         int N = reference.size(); // number of words in reference
         Iterator<String> it = candidate.iterator();
         while (it.hasNext()) {
             String s = it.next();
             int i = reference.indexOf(s);
             if (i >= 0) {
                 it.remove();
                 reference.remove(i);
             }
         }
         int rN = reference.size();
         int cN = candidate.size();
         int S = Math.min(rN, cN); // Substitution
         int DI = Math.max(rN, cN) - S; // Deletion or Insertion

         // TODO improve the formula of WACC, maybe 1/3 should be applied to DI,
         //      to weigh more on match/substitute.

         // 100*(1 - (S + 0.5*D + 0.5*I)/N)
         return 50 * (2*(N-S)-DI) / N;
     }

     //
     // Private Classes

     private static class VTimeZone {
         public VTimeZone() {
         }

         public String mTzId = null;
         public String mJavaId = null;

         public TzParam mStdParam = null;
         public TzParam mDstParam = null;

         static class TzParam {
             String name;
             Time dtStart;
             String offsetStr;
             int offset; // in millis
             String rrule;

             @Override
             public String toString() {
                 return name + " [" + dtStart.format2445() + ", " + offsetStr + ", '" + rrule + "']";
             }
         }
     };
}
