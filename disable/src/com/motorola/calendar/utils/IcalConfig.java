package com.motorola.calendar.utils;

public class IcalConfig {

    public static final boolean ENABLE_ICAL_FEATURE = false;

    private IcalConfig() {
    }

    /*
     * Gets settings to enable/disable legal workaround of calendar. This should
     * be enabled in phones traded on US market due to litigation issues.
     */
    public static boolean isICalFeatureEnabled() {
        return ENABLE_ICAL_FEATURE;
    }
}