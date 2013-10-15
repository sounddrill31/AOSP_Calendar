# ============================================================
# MotoCalendar - Android.mk
# ============================================================
#
# ============================================================
# MotoCalendar = Exported MotoCalendar Library
# ============================================================

LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

# Build all java files in the java subdirectory
LOCAL_SRC_FILES := \
    $(call all-java-files-under, src)

# The name of the jar file to create
LOCAL_MODULE := com.motorola.calendarcommon
LOCAL_MODULE_TAGS := optional

LOCAL_REQUIRED_MODULES := com.motorola.calendarcommon.xml

LOCAL_ADDITIONAL_DROIDDOC_OPTIONS := com.motorola.calendarcommon.CalendarHelper

# Build a static jar file.
include $(BUILD_JAVA_LIBRARY)

# ============================================================
# Install the permissions file into system/etc/permissions
# ============================================================
include $(CLEAR_VARS)
LOCAL_MODULE := com.motorola.calendarcommon.xml
LOCAL_MODULE_TAGS := optional
LOCAL_MODULE_CLASS := ETC

LOCAL_MODULE_PATH := $(TARGET_OUT_ETC)/permissions
LOCAL_SRC_FILES := $(LOCAL_MODULE)

include $(BUILD_PREBUILT)
