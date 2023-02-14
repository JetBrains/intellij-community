package com.jetbrains.performancePlugin.commands;

import com.intellij.openapi.ui.playback.PlaybackContext;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.jetbrains.performancePlugin.utils.AbstractCallbackBasedCommand;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public final class ReloadFilesCommand extends AbstractCallbackBasedCommand {

  public static final @NonNls String PREFIX = CMD_PREFIX + "reloadFiles";

  public ReloadFilesCommand(@NotNull String text, int line) {
    super(text, line, true);
  }

  @Override
  protected void execute(@NotNull ActionCallback callback, @NotNull PlaybackContext context)  {
      LocalFileSystem.getInstance().refresh(false);
      callback.setDone();
  }
}