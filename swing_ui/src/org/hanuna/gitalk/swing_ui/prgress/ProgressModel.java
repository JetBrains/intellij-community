package org.hanuna.gitalk.swing_ui.prgress;

import org.jetbrains.annotations.NotNull;

/**
 * @author erokhins
 */
public class ProgressModel {
    private Updater updater;
    private State state = State.HIDE;
    private String message = "";
    private float progress = 0.0f;

    public void setUpdater(@NotNull Updater updater) {
        this.updater = updater;
    }

    private void runUpdater() {
        if (updater != null) {
            updater.runUpdate(state, message, progress);
        }
    }

    public void setProgress(float progress) {
        this.progress = progress;
        runUpdater();
    }

    public void setMessage(@NotNull String message) {
        this.message = message;
        runUpdater();
    }

    public void setState(@NotNull State state) {
        this.state = state;
        runUpdater();
    }

    public enum State {
        HIDE,
        UNREFINED_PROGRESS,
        PROGRESS
    }

    public interface Updater {
        public void runUpdate(@NotNull State state, @NotNull String message, float progress);
    }

}
