package de.plushnikov.intellij.plugin.action;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiJavaFile;
import com.intellij.openapi.roots.LombokLibraryUtil;
import org.jetbrains.annotations.NotNull;

public class LombokMenuGroup extends DefaultActionGroup implements DumbAware {

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    final Project project = e.getProject();
    final boolean shouldShow =
      e.getData(CommonDataKeys.PSI_FILE) instanceof PsiJavaFile && project != null && LombokLibraryUtil.hasLombokLibrary(project);
    e.getPresentation().setEnabledAndVisible(shouldShow);
  }
}
