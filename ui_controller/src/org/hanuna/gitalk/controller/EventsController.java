package org.hanuna.gitalk.controller;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author erokhins
 */
public class EventsController {
    private final List<ControllerListener> listeners = new ArrayList<ControllerListener>();

    public void addListener(@NotNull ControllerListener listener) {
        listeners.add(listener);
    }

    public void runJumpToRow(int rowIndex) {
        for (ControllerListener listener : listeners) {
            listener.jumpToRow(rowIndex);
        }
    }

    public void runUpdateTable() {
        for (ControllerListener listener : listeners) {
            listener.updateTable();
        }
    }

    public static interface ControllerListener {

        public void jumpToRow(int rowIndex);
        public void updateTable();

    }
}
