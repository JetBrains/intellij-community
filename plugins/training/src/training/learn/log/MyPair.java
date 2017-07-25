package training.learn.log;

import java.util.Date;

/**
 * Created by karashevich on 13/10/15.
 */
public class MyPair{

    public Date first;
    public String second;

    public MyPair() {
        first = null;
        second = null;
    }

    public MyPair(Date date, String actionString) {
        first = date;
        second = actionString;
    }

    public Date getFirst() {
        return first;
    }

    public void setFirst(Date first) {
        this.first = first;
    }

    public String getSecond() {
        return second;
    }

    public void setSecond(String second) {
        this.second = second;
    }

    @Override
    public String toString() {
        return "<" + first +
                ", " + second + ">";
    }
}