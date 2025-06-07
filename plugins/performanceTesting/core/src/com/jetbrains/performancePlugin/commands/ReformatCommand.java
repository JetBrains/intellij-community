// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands;

import com.intellij.codeInsight.actions.ReformatCodeProcessor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.playback.PlaybackContext;
import com.intellij.openapi.ui.playback.commands.AbstractCommand;
import com.intellij.openapi.util.ActionCallback;
import com.jetbrains.performancePlugin.PerformanceTestingBundle;
import com.jetbrains.performancePlugin.utils.ActionCallbackProfilerStopper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

/**
 * Command reformat project
 */
public class ReformatCommand extends AbstractCommand {
  public static final String PREFIX = CMD_PREFIX + "reformat";

  public ReformatCommand(@NotNull String text, int line) {
    super(text, line);
  }

  @Override
  protected @NotNull Promise<Object> _execute(@NotNull PlaybackContext context) {
    ActionCallback myActionCallback = new ActionCallbackProfilerStopper();
    ReformatCodeProcessor codeProcessor = new ReformatCodeProcessor(context.getProject(), false);
    codeProcessor.setPostRunnable(() -> {
      context.message(PerformanceTestingBundle.message("command.reformat.finish"), getLine());
      myActionCallback.setDone();
    });
    ApplicationManager.getApplication().invokeLater(() -> codeProcessor.run());
    return Promises.toPromise(myActionCallback);
  }
}
