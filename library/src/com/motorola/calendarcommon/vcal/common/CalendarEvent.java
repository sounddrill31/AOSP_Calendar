package com.motorola.calendarcommon.vcal.common;

import android.provider.CalendarContract;

import java.util.Iterator;
import java.util.Vector;

/**
 * @hide
 */
public class CalendarEvent {
    public static final long INVALID_MIILISEC = -1L;

    /**
    * Class to hold Attendees and its params
    */
    public static class Attendee {
        public String name = "";
        public String email = "";
        public int relationship = CalendarContract.Attendees.RELATIONSHIP_NONE;
        public int type = CalendarContract.Attendees.TYPE_NONE;
        public int status = CalendarContract.Attendees.ATTENDEE_STATUS_NONE;
    }
    /**
    * Class to hold extended property name and value
    */
    public static class ExtProp {
        public String name = "";
        public String value = "";
    }
    /**
    * Class to hold Alarm params
    */
    public static class Alarm {
        //Epoch
        public long alarmTime;

        public int state = 0;
        public long minutes;

        public int method;
    }

    public String summary = "";
    public String location = "";
    public String description = "";
    public int status;
    public long dtStart = INVALID_MIILISEC;
    public long dtEnd = INVALID_MIILISEC;
    public long lastDate = INVALID_MIILISEC;
    public long due = INVALID_MIILISEC;
    public String duration = "";
    public boolean isAllDay;
    public boolean isTransparent;
    public String rrule = "";
    public String exrule = "";
    public String tzId = "UTC"; // Java Timezone Id
    public boolean hasAlarm = false;
    public boolean hasExtendedProperties = false;

    public long dtStamp = INVALID_MIILISEC;
    public String uid = "";
    public boolean isVtodo = false;
    public long comlpetedDate = INVALID_MIILISEC;
    public String categories = "";
    public int priority = 0;


    //Epoch time
    //public Vector<Long> rdates = new Vector<Long>();
    //Epoch time
    //public Vector<Long> exdate = new Vector<Long>();
    public String rdate = "";
    public String exdate = "";

    public Vector<Attendee> attendees = new Vector<Attendee>();

    public Attendee organizer;

    public Vector<ExtProp> extendedProperties = new Vector<ExtProp>();

    public Vector<Alarm> alarms = new Vector<Alarm>();

    public Alarm alarm = null;

    /**
    * Convert CalendarEvent to readable string
    */
    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>\n");
        sb.append("Summary=" + summary + "\n");
        sb.append("Location=" + location + "\n");
        sb.append("Description=" + description + "\n");
        sb.append("Status=" + status + "\n");
        sb.append("Allday=" + isAllDay + "\n");
        sb.append("Transparent=" + isTransparent + "\n");
        sb.append("RRULE=" + rrule + "\n");
        sb.append("EXRULE=" + exrule + "\n");
        sb.append("DTSTART=" + dtStart + "\n");
        sb.append("DTEND=" + dtEnd + "\n");
        sb.append("COMPLETED=" + comlpetedDate + "\n");
        sb.append("DUE=" + due + "\n");
        sb.append("TZ=" + tzId + "\n");
        sb.append("DURATION=" + duration + "\n");
        sb.append("LASTDATE=" + lastDate + "\n");
        sb.append("CATEGORIES=" + categories + "\n");
        sb.append("PRIORITY=" + priority + "\n");
        //sb.append("ORGANIZER=" + organizer + "\n");

        Iterator<Attendee> it = attendees.iterator();
        while(it.hasNext()) {
            sb.append("Attendee: \n");
            Attendee att = it.next();
            sb.append("\tName:" + att.name + "\n");
            sb.append("\tEmail:" + att.email + "\n");
            sb.append("\tRelationship:" + att.relationship + "\n");
            sb.append("\tType:" + att.type + "\n");
            sb.append("\tStatus:" + att.status + "\n");
        }
        sb.append("Extended Properties:\n");
        Iterator<ExtProp> extIt = extendedProperties.iterator();
        while(extIt.hasNext()) {
            ExtProp ext = extIt.next();
            sb.append("Name:" + ext.name + "\n");
            sb.append("Value:" + ext.value + "\n");
        }
        Iterator<Alarm> alarmIt = alarms.iterator();
        while(alarmIt.hasNext()) {
            sb.append("Alarm: \n");
            Alarm alarm = alarmIt.next();
            sb.append("Time:" + alarm.alarmTime + "\n");
            sb.append("State:" + alarm.state + "\n");
            sb.append("Minutes:" + alarm.minutes + "\n");
            sb.append("Method:" + alarm.method + "\n");
        }
        sb.append("<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<\n");
        return sb.toString();
    }
}
