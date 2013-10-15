/*
 * Copyright (C) 2010 Motorola, Inc.
 * All Rights Reserved
 *
 * The content of this file is copied from frameworks/opt/calendar.
 * Becasue calendar app is also using the same source code, so we
 * have a separate copy here to avoid conflict.
 *
 * Modification History:
 **********************************************************
 * Date           Author       Comments
 * 14-Aug-2012    a21263       Initial
 **********************************************************
 */

package com.motorola.androidcommon;

public class DateException extends Exception
{
    public DateException(String message)
    {
        super(message);
    }
}
