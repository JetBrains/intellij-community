package org.hanuna.gitalk.common;

import org.jetbrains.annotations.NotNull;

import java.util.Date;

/**
 * @author erokhins
 */
public class Timer {
    private Date date = new Date();
    private String message = "timer:";

    public Timer() {}

    public Timer(@NotNull String message) {
        this.message = message;
    }

    public void clear() {
        date = new Date();
    }

    public long get() {
        return new Date().getTime() - date.getTime();
    }

    public void print() {
        long ms = get();
        System.out.println(message + ":" + ms);
    }
}
