package org.hanuna.gitalk.ui.impl;

import org.hanuna.gitalk.ui.ControllerListener;
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

    public void runUpdateUI() {
        for (ControllerListener listener : listeners) {
            listener.updateUI();
        }
    }

    public void setState(@NotNull ControllerListener.State state) {
        for (ControllerListener listener : listeners) {
            listener.setState(state);
        }
    }

    public void setErrorMessage(@NotNull String errorMessage) {
        for (ControllerListener listener : listeners) {
            listener.setErrorMessage(errorMessage);
        }
    }

    public void setUpdateProgressMessage(@NotNull String progressMessage) {
        for (ControllerListener listener : listeners) {
            listener.setUpdateProgressMessage(progressMessage);
        }
    }

    public void removeAllListeners() {
        listeners.clear();
    }

}
