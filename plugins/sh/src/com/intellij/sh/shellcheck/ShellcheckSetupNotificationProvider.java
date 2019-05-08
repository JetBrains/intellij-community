package com.intellij.sh.shellcheck;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.sh.ShFileType;
import com.intellij.sh.settings.ShSettings;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotifications;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.sh.shellcheck.ShShellcheckUtil.isValidPath;

public class ShellcheckSetupNotificationProvider extends EditorNotifications.Provider<EditorNotificationPanel> {
  private static final Key<EditorNotificationPanel> KEY = Key.create("sh.shellcheck.installation");

  @NotNull
  @Override
  public Key<EditorNotificationPanel> getKey() {
    return KEY;
  }

  @Nullable
  @Override
  public EditorNotificationPanel createNotificationPanel(@NotNull VirtualFile file, @NotNull FileEditor fileEditor, @NotNull Project project) {
    ShSettings settings = ShSettings.getInstance();
    if (file.getFileType() instanceof ShFileType && !isValidPath(settings.getShellcheckPath())) {
      EditorNotificationPanel panel = new EditorNotificationPanel();
      panel.setText("Would you like to install shellcheck to verify your shell scripts?");
      panel.createActionLabel("Install", () -> ShShellcheckUtil.download(null, () -> {
        EditorNotifications.getInstance(project).updateAllNotifications();
        PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
        if (psiFile != null) {
          DaemonCodeAnalyzer.getInstance(project).restart(psiFile);
        }
      }));
      return panel;
    }
    return null;
  }
}
