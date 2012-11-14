package org.hanuna.gitalk.graphmodel.builder;

import org.hanuna.gitalk.common.Interval;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

/**
 * @author erokhins
 */
public class RemoveIntervalArrayList<T> extends ArrayList<T> {

    /**
     * @param interval remove [from, to) elements
     */
    public void removeInterval(@NotNull Interval interval) {
        removeRange(interval.from(), interval.to());
    }
}
