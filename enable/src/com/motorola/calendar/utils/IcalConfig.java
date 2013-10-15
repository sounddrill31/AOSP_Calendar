package com.motorola.calendar.utils;

public class IcalConfig {

    public static final boolean ENABLE_ICAL_FEATURE = true;

    private IcalConfig() {
    }

    public static boolean isICalFeatureEnabled() {
        return ENABLE_ICAL_FEATURE;
    }
}