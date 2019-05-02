package com.intellij.bash.shellcheck;

import com.intellij.bash.BashFileType;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotifications;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.bash.shellcheck.BashShellcheckUtil.getShellcheckPath;
import static com.intellij.bash.shellcheck.BashShellcheckUtil.isValidPath;

public class ShellcheckSetupNotificationProvider extends EditorNotifications.Provider<EditorNotificationPanel> {
  private static final Key<EditorNotificationPanel> KEY = Key.create("bash.shellcheck.installation");

  @NotNull
  @Override
  public Key<EditorNotificationPanel> getKey() {
    return KEY;
  }

  @Nullable
  @Override
  public EditorNotificationPanel createNotificationPanel(@NotNull VirtualFile file, @NotNull FileEditor fileEditor, @NotNull Project project) {
    if (file.getFileType() instanceof BashFileType && !isValidPath(getShellcheckPath())) {
      EditorNotificationPanel panel = new EditorNotificationPanel();
      panel.setText("Bash shellcheck not installed or incorrect path");
      panel.createActionLabel("Download", () -> {
        BashShellcheckUtil.download(null, null);
        EditorNotifications.getInstance(project).updateAllNotifications();
      });
      return panel;
    }
    return null;
  }
}
