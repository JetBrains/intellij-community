// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.playback.PlaybackContext;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.performancePlugin.utils.AbstractCallbackBasedCommand;
import kotlin.Suppress;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.intellij.openapi.vfs.VfsUtil.markDirtyAndRefresh;
import static com.jetbrains.performancePlugin.commands.OpenFileCommand.findFile;

/**
 * Command to reload project files from disk.
 * Example: %reloadFiles
 * Example: %reloadFiles filePath1 filePath2 - marks filePath1 filePath2 as dirty and then reload
 */
public final class ReloadFilesCommand extends AbstractCallbackBasedCommand {

  public static final @NonNls String PREFIX = CMD_PREFIX + "reloadFiles";

  public ReloadFilesCommand(@NotNull String text, int line) {
    super(text, line, true);
  }

  // Calls from driver
  @Suppress(names = "UNUSED")
  public ReloadFilesCommand() {
    super("", 0);
  }

  // Calls from driver
  @Suppress(names = "UNUSED")
  public static void synchronizeFiles(List<String> filePaths) {
    synchronizeFiles(filePaths, ProjectManager.getInstance().getOpenProjects()[0]);
  }

  @Override
  protected void execute(@NotNull ActionCallback callback, @NotNull PlaybackContext context) {
    var project = context.getProject();
    synchronizeFiles(extractCommandList(PREFIX, " "), project);
    callback.setDone();
  }

  private static void synchronizeFiles(List<String> filePaths, Project project) {
    VirtualFile[] files = filePaths.stream().map(path -> {
      var file = findFile(path, project);
      if (file == null) {
        throw new IllegalArgumentException("File not found " + path);
      }
      return file;
    }).toArray(VirtualFile[]::new);
    markDirtyAndRefresh(false, true, true, files);
  }
}