package com.jetbrains.performancePlugin.commands;

import com.intellij.ide.projectView.ProjectView;
import com.intellij.openapi.ui.playback.PlaybackContext;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.performancePlugin.utils.AbstractCallbackBasedCommand;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import static com.jetbrains.performancePlugin.commands.OpenFileCommand.findFile;

public class SelectFileInProjectViewCommand extends AbstractCallbackBasedCommand {
  public static final @NonNls String PREFIX = "%selectFileInProjectView";

  public SelectFileInProjectViewCommand(@NotNull String text, int line) {
    super(text, line, true);
  }

  @Override
  protected void execute(@NotNull ActionCallback callback,
                         @NotNull PlaybackContext context) throws Exception {
    String filePath = getText().split(" ", 2)[1];
    VirtualFile file = findFile(filePath, context.getProject());
    ProjectView.getInstance(context.getProject()).select(null, file, true);
    callback.setDone();
  }
}
