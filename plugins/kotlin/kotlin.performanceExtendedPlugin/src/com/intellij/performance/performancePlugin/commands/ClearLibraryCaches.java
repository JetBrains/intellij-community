// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.performance.performancePlugin.commands;

import com.intellij.java.library.JavaLibraryModificationTracker;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.ui.playback.PlaybackContext;
import com.intellij.openapi.ui.playback.commands.AbstractCommand;
import com.intellij.openapi.util.ActionCallback;
import com.jetbrains.performancePlugin.utils.ActionCallbackProfilerStopper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

public class ClearLibraryCaches extends AbstractCommand {

  public static final String PREFIX = CMD_PREFIX + "clearLibraryCaches";

  public ClearLibraryCaches(@NotNull String text, int line) {
    super(text, line);
  }

  @Override
  protected @NotNull Promise<Object> _execute(@NotNull PlaybackContext context) {
    final ActionCallback actionCallback = new ActionCallbackProfilerStopper();
    WriteAction.runAndWait(() -> {
        JavaLibraryModificationTracker.incModificationCount(context.getProject());
      actionCallback.setDone();
    });
    return Promises.toPromise(actionCallback);
  }
}
