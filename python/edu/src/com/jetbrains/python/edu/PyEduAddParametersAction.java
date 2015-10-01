package com.jetbrains.python.edu;

import com.intellij.execution.actions.EditRunConfigurationsAction;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;

public class PyEduAddParametersAction implements PyExecuteFileExtensionPoint {
  @NotNull
  @Override
  public AnAction getRunAction() {
    return new AddParametersAction();
  }

  private static class AddParametersAction extends EditRunConfigurationsAction {
    @Override
    public void update(AnActionEvent e) {
      super.update(e);
      e.getPresentation().setText("Add Parameters");
    }
  }
}
