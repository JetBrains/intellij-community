package com.intellij.openapi.samples;

import java.util.Calendar;

/**
 * Created by IntelliJ IDEA.
 * User: Alexey.Chursin
 * Date: Sep 3, 2010
 * Time: 3:48:51 PM
 * To change this template use File | Settings | File Templates.
 */
public class DateTimeClass {
    protected String currentDate;
    protected String currentTime;

    DateTimeClass(){
        this.currentDateTime();
    }

    public void currentDateTime() {
        // Get current date and time
        Calendar instance = Calendar.getInstance();
        currentDate =String.valueOf(instance.get(Calendar.DAY_OF_MONTH)) + "/"
                + String.valueOf(instance.get(Calendar.MONTH) + 1) + "/" + String.valueOf(instance.get(Calendar.YEAR));
        int min = instance.get(Calendar.MINUTE);
        String strMin;
        if (min < 10) {
            strMin = "0" + String.valueOf(min);
        } else {
            strMin = String.valueOf(min);
        }
        currentTime=instance.get(Calendar.HOUR_OF_DAY) + ":" + strMin;



    }

}
