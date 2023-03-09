package com.jetbrains.performancePlugin.commands;

import com.intellij.openapi.ui.playback.PlaybackContext;
import com.intellij.openapi.ui.playback.commands.AbstractCommand;
import com.intellij.openapi.vfs.newvfs.RefreshQueue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;

import static java.util.Objects.requireNonNull;

public class WaitForAsyncRefreshCommand extends AbstractCommand {
  public static final String PREFIX = CMD_PREFIX + "waitForAsyncRefresh";

  public WaitForAsyncRefreshCommand(@NotNull String text, int line) {
    super(text, line);
  }

  @Override
  protected @NotNull Promise<Object> _execute(@NotNull PlaybackContext context) {
    var file = requireNonNull(context.getProject().getProjectFile(), "project=" + context.getProject());
    var promise = new AsyncPromise<>();
    RefreshQueue.getInstance().refresh(true, true, () -> promise.setResult(null), file);
    return promise;
  }
}
