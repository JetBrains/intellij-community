/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.text;

import com.intellij.openapi.util.text.StringUtil;

import java.util.Date;
import java.util.Calendar;
import java.util.Locale;
import java.text.SimpleDateFormat;
import java.text.DateFormat;

public class DateFormatUtil {
  public static final long SECOND = 1000;
  public static final long MINUTE = SECOND * 60;
  public static final long HOUR = MINUTE * 60;
  public static final long DAY = HOUR * 24;
  public static final long WEEK = DAY * 7;
  public static final long MONTH = DAY * 30;
  public static final long YEAR = DAY * 365;

  public static final long[] DELIMS = new long[] {YEAR, MONTH, WEEK, DAY, HOUR, MINUTE};
  public static final String[] NAMES = new String[] {"year", "month", "week", "day", "hour", "minute"};

  public static String formatDuration(long delta) {
    StringBuffer buf = new StringBuffer();
    for (int i = 0; i < DELIMS.length; i++) {
      long delim = DELIMS[i];
      int n = (int)(delta / delim);
      if (n != 0) {
        buf.append(String.valueOf(n));
        buf.append(' ');
        buf.append(StringUtil.pluralize(NAMES[i], n));
        buf.append(' ');
        delta = delta % delim;
      }
    }

    if (buf.length() == 0) return "less than a minute";
    return buf.toString().trim();
  }

  public static String formatBetweenDates(long d1, long d2) {
    long delta = Math.abs(d1 - d2);
    if (delta == 0) return "right now";

    StringBuffer buf = new StringBuffer();
    if (d2 < d1) {
      buf.append("in ");
    }

    int i;
    for (i = 0; i < DELIMS.length; i++) {
      long delim = DELIMS[i];
      if (delta >= delim) {
        int n = (int)(delta / delim);
        buf.append(numeric(n));
        buf.append(" ");
        buf.append(StringUtil.pluralize(NAMES[i], n));
        break;
      }
    }

    if (i >= DELIMS.length) {
      buf.append("a few moments");
    }

    if (d2 > d1) {
      buf.append(" ago");
    }

    return buf.toString();
  }

  public static String numeric(int n) {
    if (n == 0) return "zero";
    if (n == 1) return "one";
    return String.valueOf(n);
  }

  public static String formatDate(Date today, Date date, Locale locale) {
    Calendar calendar = Calendar.getInstance();
    calendar.setTime(today);

    int todayYear = calendar.get(Calendar.YEAR);
    int todayDayOfYear = calendar.get(Calendar.DAY_OF_YEAR);

    calendar.setTime(date);

    int year = calendar.get(Calendar.YEAR);
    int dayOfYear = calendar.get(Calendar.DAY_OF_YEAR);

    DateFormat defaultDateFormat = SimpleDateFormat.getDateTimeInstance(SimpleDateFormat.SHORT,
                                                                              SimpleDateFormat.SHORT,
                                                                              locale);

    DateFormat timeDefaultFormat = DateFormat.getTimeInstance(SimpleDateFormat.SHORT, locale);

    boolean isYesterdayOnPreviousYear = (todayYear == year + 1) && todayDayOfYear == 1 && dayOfYear == calendar.getActualMaximum(Calendar.DAY_OF_YEAR);

    boolean isYesterday = isYesterdayOnPreviousYear || (todayYear == year && todayDayOfYear == dayOfYear + 1);

    if (isYesterday) {
      return "Yesterday " + timeDefaultFormat.format(date);
    } else if (year != todayYear) {
      return defaultDateFormat.format(date);
    } else if (todayDayOfYear == dayOfYear) {
      return "Today " + timeDefaultFormat.format(date);
    } else {
      return defaultDateFormat.format(date);
    }
  }

  public static String formatDate(Date current_date, Date date) {
    return formatDate(current_date, date, Locale.getDefault());
  }
}