package br.com.training.ble_tests;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Provides useful methods for handling date/time.
 *
 * @author @copyright Copyright (c) 2017, NUTES UEPB
 */
public final class DateUtils {
    public static final String DATE_FORMAT_DATE_TIME = "yyyy-MM-dd'T'HH:mm:ss";


    /**
     * Returns the current datetime in format of formatDate string passed as parameter.
     * If "formatDate" is null the default value: "yyyy-MM-dd HH:mm:ss" will be used.
     *
     * @param formatDate The datetime format
     * @return Datetime formatted
     */
    public static String getCurrentDatetime(String formatDate) {
        if (formatDate == null) formatDate = "yyyy-MM-dd HH:mm:ss";

        Calendar calendar = GregorianCalendar.getInstance();

        DateFormat dateFormat = new SimpleDateFormat(formatDate, Locale.getDefault());
        return dateFormat.format(calendar.getTime());
    }

    /**
     * Retrieve the current date according to timezone UTC.
     *
     * @return String
     */
    public static String getCurrentDateTimeUTC() {
        SimpleDateFormat format = new SimpleDateFormat(DateUtils.DATE_FORMAT_DATE_TIME, Locale.getDefault());
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date());
    }
}
