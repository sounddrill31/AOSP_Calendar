package com.motorola.calendarcommon.vcal.composer;

/**
 * @hide
 */
public interface VCalComposer {
    /**
     * Compose vCal(v1.0) or iCal(v2.0) event based on calendar event
     * @return vCal or iCal utf-8 stream
     */
    public byte[] composeVCal();
}
