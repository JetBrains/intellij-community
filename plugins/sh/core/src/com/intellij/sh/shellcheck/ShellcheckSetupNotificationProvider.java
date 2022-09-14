// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.shellcheck;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
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

import static com.intellij.sh.ShBundle.message;
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
  public EditorNotificationPanel createNotificationPanel(@NotNull VirtualFile file,
                                                         @NotNull FileEditor fileEditor,
                                                         @NotNull Project project) {
    if (file.getFileType() instanceof ShFileType && !isValidPath(ShSettings.getShellcheckPath())) {
      EditorNotificationPanel panel = new EditorNotificationPanel(fileEditor, EditorNotificationPanel.Status.Info);
      panel.setText(message("sh.shellcheck.install.question"));
      Runnable onSuccess = () -> {
        EditorNotifications.getInstance(project).updateAllNotifications();
        PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
        if (psiFile != null) DaemonCodeAnalyzer.getInstance(project).restart(psiFile);
        Notifications.Bus.notify(new Notification(message("sh.shell.script"), message("sh.shell.script"), message("sh.shellcheck.success.install"),
                                                  NotificationType.INFORMATION));
      };
      Runnable onFailure = () -> Notifications.Bus.notify(new Notification(message("sh.shell.script"), message("sh.shell.script"),
                                                                           message("sh.shellcheck.cannot.download"),
                                                                           NotificationType.ERROR));
      panel.createActionLabel(message("sh.install"), () -> ShShellcheckUtil.download(null, onSuccess, onFailure));
      //noinspection DialogTitleCapitalization
      panel.createActionLabel(message("sh.no.thanks"), () -> {
        ShSettings.setShellcheckPath(ShSettings.I_DO_MIND_SUPPLIER.get());
        EditorNotifications.getInstance(project).updateAllNotifications();
      });
      return panel;
    }
    return null;
  }
}
