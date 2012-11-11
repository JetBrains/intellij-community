package org.hanuna.gitalk.common.rows;

/**
 * @author erokhins
 */
public class Interval {
    private final int from;
    private final int to;

    public Interval(int from, int to) {
        this.from = from;
        this.to = to;
    }

    public int from() {
        return from;
    }

    public int to() {
        return to;
    }
}
