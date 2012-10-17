package org.hanuna.gitalk.swingui;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * @author erokhins
 */
public class DateConverter {
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat ("dd/MM/yy HH:mm");

    public static String getStringOfDate(long secondsTimeStamp) {
        Date date = new Date(secondsTimeStamp * 1000);
        return dateFormat.format(date);
    }
}
