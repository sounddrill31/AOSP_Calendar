LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

# Include res dir from chips
chips_dir := ../../../frameworks/ex/chips/res
color_picker_dir := ../../../frameworks/opt/colorpicker/res
datetimepicker_dir := ../../../frameworks/opt/datetimepicker/res
timezonepicker_dir := ../../../frameworks/opt/timezonepicker/res
res_dirs := $(chips_dir) $(color_picker_dir) $(datetimepicker_dir) $(timezonepicker_dir) res
src_dirs := src

LOCAL_EMMA_COVERAGE_FILTER := +com.android.calendar.*

LOCAL_MODULE_TAGS := optional

LOCAL_SRC_FILES := $(call all-java-files-under,$(src_dirs))

# bundled
#LOCAL_STATIC_JAVA_LIBRARIES += \
#        android-common \
#        android-common-chips \
#        calendar-common

# unbundled
LOCAL_STATIC_JAVA_LIBRARIES := \
        android-common \
        android-common-chips \
        colorpicker \
        android-opt-datetimepicker \
        android-opt-timezonepicker \
        android-support-v4 \
        calendar-common

LOCAL_SDK_VERSION := current

LOCAL_RESOURCE_DIR := $(addprefix $(LOCAL_PATH)/, $(res_dirs))

# Begin Motorola, IKJB42MAIN-55 / Porting iCal feature for FEATURE-3247
ifeq ($(PRODUCT_REQUIRES_ICAL_CALENDAR_FEATURE),true)
   LOCAL_SRC_FILES += enable/src/com/motorola/calendar/utils/IcalConfig.java
else
   LOCAL_SRC_FILES += disable/src/com/motorola/calendar/utils/IcalConfig.java
endif
# End Motorola, IKJB42MAIN-55 / Porting iCal feature for FEATURE-3247

LOCAL_PACKAGE_NAME := Calendar

# Begin Motorola, IKJB42MAIN-55 / Porting iCal feature for FEATURE-3247
LOCAL_JAVA_LIBRARIES := \
    com.motorola.calendarcommon

LOCAL_REQUIRED_MODULES := \
    com.motorola.calendarcommon
# End Motorola, IKJB42MAIN-55 / Porting iCal feature for FEATURE-3247

LOCAL_PROGUARD_FLAG_FILES := proguard.flags \
                             ../../../frameworks/opt/datetimepicker/proguard.flags

LOCAL_AAPT_FLAGS := --auto-add-overlay
LOCAL_AAPT_FLAGS += --extra-packages com.android.ex.chips
LOCAL_AAPT_FLAGS += --extra-packages com.android.colorpicker
LOCAL_AAPT_FLAGS += --extra-packages com.android.datetimepicker
LOCAL_AAPT_FLAGS += --extra-packages com.android.timezonepicker

include $(BUILD_PACKAGE)

# Use the following include to make our test apk.
include $(call all-makefiles-under,$(LOCAL_PATH))
