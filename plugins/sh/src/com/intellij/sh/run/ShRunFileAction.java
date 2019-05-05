package com.intellij.sh.run;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.sh.psi.ShFile;
import org.jetbrains.annotations.NotNull;

public class ShRunFileAction extends DumbAwareAction {
  static final String ID = "runShellFileAction";

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    PsiFile file = e.getData(PlatformDataKeys.PSI_FILE);
    if (!(file instanceof ShFile)) return;

    Project project = file.getProject();
    ShRunner runner = findRunner(project);
    runner.run((ShFile) file);
  }

  @NotNull
  private static ShRunner findRunner(@NotNull Project project) {
    ShRunner runner = ServiceManager.getService(project, ShRunner.class);
    return runner == null || !runner.isAvailable(project)
        ? new ShFailoverRunner()
        : runner;
  }

  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(isEnabled(e));
  }

  private static boolean isEnabled(@NotNull AnActionEvent e) {
    return e.getProject() != null && e.getData(PlatformDataKeys.PSI_FILE) instanceof ShFile;
  }
}