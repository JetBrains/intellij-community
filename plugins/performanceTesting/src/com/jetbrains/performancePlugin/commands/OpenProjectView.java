package com.jetbrains.performancePlugin.commands;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.playback.PlaybackContext;
import com.intellij.openapi.ui.playback.commands.AbstractCommand;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.jetbrains.performancePlugin.utils.ActionCallbackProfilerStopper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

public class OpenProjectView extends AbstractCommand {
  public static final String PREFIX = CMD_PREFIX + "openProjectView";
  private static final Logger LOG = Logger.getInstance(OpenProjectView.class);

  public OpenProjectView(@NotNull String text, int line) {
    super(text, line);
  }

  @NotNull
  @Override
  protected Promise<Object> _execute(@NotNull final PlaybackContext context) {
    final ActionCallback actionCallback = new ActionCallbackProfilerStopper();
    ApplicationManager.getApplication().invokeLater(() -> {
      ToolWindowManager windowManager = ToolWindowManager.getInstance(context.getProject());
      ToolWindow window = windowManager.getToolWindow(ToolWindowId.PROJECT_VIEW);
      if (!window.isActive() &&
          (windowManager.isEditorComponentActive() || !ToolWindowId.PROJECT_VIEW.equals(windowManager.getActiveToolWindowId()))) {
        window.activate(null);
        LOG.warn("Project View is opened");
      }
      else {
        LOG.warn("Project View has been opened already");
      }
      actionCallback.setDone();
    });
    return Promises.toPromise(actionCallback);
  }
}