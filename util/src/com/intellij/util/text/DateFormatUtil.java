/*
 * Copyright 2000-2005 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util.text;

import com.intellij.CommonBundle;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class DateFormatUtil {
  public static final long SECOND = 1000;
  public static final long MINUTE = SECOND * 60;
  public static final long HOUR = MINUTE * 60;
  public static final long DAY = HOUR * 24;
  public static final long WEEK = DAY * 7;
  public static final long MONTH = DAY * 30;
  public static final long YEAR = DAY * 365;

  public static final long[] DELIMS = new long[] {YEAR, MONTH, WEEK, DAY, HOUR, MINUTE};

  private DateFormatUtil() {
  }

  enum Period {
    YEAR, MONTH, WEEK, DAY, HOUR, MINUTE
  }

  private static final Period[] PERIOD = new Period[] {Period.YEAR, Period.MONTH, Period.WEEK, Period.DAY, Period.HOUR, Period.MINUTE};

  public static String formatDuration(long delta) {
    StringBuffer buf = new StringBuffer();
    for (int i = 0; i < DELIMS.length; i++) {
      long delim = DELIMS[i];
      int n = (int)(delta / delim);
      if (n != 0) {
        buf.append(composeDurationMessage(PERIOD[i], n));
        buf.append(' ');
        delta = delta % delim;
      }
    }

    if (buf.length() == 0) return CommonBundle.message("date.format.less.than.a.minute");
    return buf.toString().trim();
  }

  private static String composeDurationMessage(final Period period, final int n) {
    switch(period) {
      case DAY: return CommonBundle.message("date.format.n.days", n);
      case MINUTE:return CommonBundle.message("date.format.n.minutes", n);
      case HOUR:return CommonBundle.message("date.format.n.hours", n);
      case MONTH:return CommonBundle.message("date.format.n.months", n);
      case WEEK:return CommonBundle.message("date.format.n.weeks", n);
      default:return CommonBundle.message("date.format.n.years", n);
    }
  }

  public static String formatBetweenDates(long d1, long d2) {
    long delta = Math.abs(d1 - d2);
    if (delta == 0) return CommonBundle.message("date.format.right.now");

    int n = -1;
    int i;
    for (i = 0; i < DELIMS.length; i++) {
      long delim = DELIMS[i];
      if (delta >= delim) {
        n = (int)(delta / delim);
        break;
      }
    }

    if (d2 > d1) {
      if (n <= 0) {
        return CommonBundle.message("date.format.a.few.moments.ago");
      } else {
        return someTimeAgoMessage(PERIOD[i], n);
      }
    }
    else if (d2 < d1) {
      if (n <= 0) {
        return CommonBundle.message("date.format.in.a.few.moments");
      } else {
        return composeInSomeTimeMessage(PERIOD[i], n);
      }

    }

    return "";
  }

  private static String composeInSomeTimeMessage(final Period period, final int n) {
    switch(period) {
      case DAY: return CommonBundle.message("date.format.in.n.days", n);
      case MINUTE:return CommonBundle.message("date.format.in.n.minutes", n);
      case HOUR:return CommonBundle.message("date.format.in.n.hours", n);
      case MONTH:return CommonBundle.message("date.format.in.n.months", n);
      case WEEK:return CommonBundle.message("date.format.in.n.weeks", n);
      default:return CommonBundle.message("date.format.in.n.years", n);
    }
  }

  private static String someTimeAgoMessage(final Period period, final int n) {
    switch(period) {
      case DAY: return CommonBundle.message("date.format.n.days.ago", n);
      case MINUTE:return CommonBundle.message("date.format.n.minutes.ago", n);
      case HOUR:return CommonBundle.message("date.format.n.hours.ago", n);
      case MONTH:return CommonBundle.message("date.format.n.months.ago", n);
      case WEEK:return CommonBundle.message("date.format.n.weeks.ago", n);
      default:return CommonBundle.message("date.format.n.years.ago", n);
    }

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
      return CommonBundle.message("date.format.yesterday") + " " + timeDefaultFormat.format(date);
    } else if (year != todayYear) {
      return defaultDateFormat.format(date);
    } else if (todayDayOfYear == dayOfYear) {
      return CommonBundle.message("date.format.today") + " " + timeDefaultFormat.format(date);
    } else {
      return defaultDateFormat.format(date);
    }
  }

  public static String formatDate(Date current_date, Date date) {
    return formatDate(current_date, date, Locale.getDefault());
  }

  public static String formatFrequency(final long tm) {
    return CommonBundle.message("date.frequency", formatBetweenDates(tm, 0));
  }
}