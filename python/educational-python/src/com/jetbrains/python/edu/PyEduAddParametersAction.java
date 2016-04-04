package com.jetbrains.python.edu;

import com.intellij.execution.actions.EditRunConfigurationsAction;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.util.PlatformUtils;
import org.jetbrains.annotations.Nullable;

public class PyEduAddParametersAction implements PyExecuteFileExtensionPoint {

  @Nullable
  @Override
  public AnAction getRunAction() {
    return new AddParametersAction();
  }

  @Override
  public boolean accept(Project project) {
    return PlatformUtils.isPyCharmEducational();
  }

  private static class AddParametersAction extends EditRunConfigurationsAction {
    @Override
    public void update(AnActionEvent e) {
      super.update(e);
      e.getPresentation().setText("Add Parameters");
    }
  }
}
