package com.motorola.calendarcommon.vcal.composer;

import java.util.List;

/**
 * This interface encapsulates the properties that characterize an ActiveSync
 * event exception.
 * @hide
 */
public interface ExceptionInfo {
    /**
     * Returns the list of categories for this Exception, each category being a String.
     * @return The list of categories for this Exception.
     */
    List<String> getCategories();

    /**
     * Returns the start time for the exception.
     * @return The exception's start time, as a YYMMDDTHHMSS string.
     */
    String getExceptionStartTime();

    /**
     * Returns whether this is a "deleted" exception.
     * @return true for a deleted exception, false for a modify exception.
     */
    boolean isDeleted();

}
