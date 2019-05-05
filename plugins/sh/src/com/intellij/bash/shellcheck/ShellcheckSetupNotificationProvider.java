package com.intellij.bash.shellcheck;

import com.intellij.bash.ShFileType;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotifications;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.bash.shellcheck.ShShellcheckUtil.getShellcheckPath;
import static com.intellij.bash.shellcheck.ShShellcheckUtil.isValidPath;

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
    if (file.getFileType() instanceof ShFileType && !isValidPath(getShellcheckPath())) {
      EditorNotificationPanel panel = new EditorNotificationPanel();
      panel.setText("Shellcheck not installed or incorrect path");
      panel.createActionLabel("Download", () -> {
        ShShellcheckUtil.download(null, null);
        EditorNotifications.getInstance(project).updateAllNotifications();
      });
      return panel;
    }
    return null;
  }
}
