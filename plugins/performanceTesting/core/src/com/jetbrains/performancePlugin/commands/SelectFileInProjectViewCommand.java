// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands;

import com.intellij.ide.projectView.ProjectView;
import com.intellij.openapi.ui.playback.PlaybackContext;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.performancePlugin.utils.AbstractCallbackBasedCommand;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import static com.jetbrains.performancePlugin.commands.OpenFileCommand.findFile;

/**
 * Command to select file in project view.
 * Example: %selectFileInProjectView MyClass.php
 */
public class SelectFileInProjectViewCommand extends AbstractCallbackBasedCommand {
  public static final @NonNls String PREFIX = "%selectFileInProjectView";

  public SelectFileInProjectViewCommand(@NotNull String text, int line) {
    super(text, line, true);
  }

  @Override
  protected void execute(@NotNull ActionCallback callback, @NotNull PlaybackContext context) {
    String filePath = getText().split(" ", 2)[1];
    VirtualFile file = findFile(filePath, context.getProject());
    ProjectView.getInstance(context.getProject()).select(null, file, true);
    callback.setDone();
  }
}
