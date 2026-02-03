// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.playback.PlaybackContext;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.performancePlugin.PerformanceTestingBundle;
import com.jetbrains.performancePlugin.utils.AbstractCallbackBasedCommand;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.TimeUnit;

import static com.jetbrains.performancePlugin.commands.OpenFileCommand.findFile;

public final class OpenFileWithTerminateCommand extends AbstractCallbackBasedCommand {

  public static final @NonNls String PREFIX = CMD_PREFIX + "openFileWithTerminate";

  public OpenFileWithTerminateCommand(@NotNull String text, int line) {
    super(text, line, true);
  }

  @Override
  protected void execute(@NotNull ActionCallback actionCallback,
                         @NotNull PlaybackContext context) {
    String[] arguments = getText().split(" ", 3);
    String filePath = arguments[1];
    long timeout = Long.parseLong(arguments[2]);
    Project project = context.getProject();

    VirtualFile file = findFile(filePath,
                                project);
    assert file != null :
      PerformanceTestingBundle.message("command.file.not.found", filePath);
    FileEditorManager.getInstance(project).openFile(file, true);
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      try {
        TimeUnit.SECONDS.sleep(timeout);
        ApplicationManager.getApplication().exit(true, true, false);
        actionCallback.setDone();
      }
      catch (InterruptedException e) {
        actionCallback.reject(e.getMessage());
      }
    });
  }
}
