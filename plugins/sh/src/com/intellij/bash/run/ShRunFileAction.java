package com.intellij.bash.run;

import com.intellij.bash.psi.ShFile;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

public class ShRunFileAction extends DumbAwareAction {
  static final String ID = "runShellFileAction";

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    PsiFile file = e.getData(PlatformDataKeys.PSI_FILE);
    if (!(file instanceof ShFile)) return;

    Project project = file.getProject();
    ShellScriptRunner shellScriptRunner = ServiceManager.getService(project, ShellScriptRunner.class);
    if (shellScriptRunner == null || !shellScriptRunner.isAvailable(project)) {
      shellScriptRunner = new FailoverShellScriptRunner();
    }
    shellScriptRunner.run((ShFile) file);
  }

  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(isEnabled(e));
  }

  private static boolean isEnabled(@NotNull AnActionEvent e) {
    return e.getProject() != null && e.getData(PlatformDataKeys.PSI_FILE) instanceof ShFile;
  }
}