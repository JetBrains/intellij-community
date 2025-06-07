// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.utils;

import com.intellij.openapi.ui.playback.PlaybackContext;
import com.intellij.openapi.ui.playback.commands.AbstractCommand;
import com.intellij.openapi.util.ActionCallback;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

public abstract class AbstractCallbackBasedCommand extends AbstractCommand {

  protected AbstractCallbackBasedCommand(@NotNull String text, int line) {
    super(text, line);
  }

  protected AbstractCallbackBasedCommand(@NotNull String text, int line, boolean executeInAwt) {
    super(text, line, executeInAwt);
  }

  @Override
  protected @NotNull Promise<Object> _execute(@NotNull PlaybackContext context) {
    ActionCallback callback = createCallback();
    try {
      execute(callback, context);
    }
    catch (Throwable throwable) {
      callback.reject(throwable.getMessage());
    }
    return Promises.toPromise(callback);
  }

  protected @NotNull ActionCallback createCallback() {
    return new ActionCallbackProfilerStopper();
  }

  protected abstract void execute(@NotNull ActionCallback callback,
                                  @NotNull PlaybackContext context) throws Exception;
}
