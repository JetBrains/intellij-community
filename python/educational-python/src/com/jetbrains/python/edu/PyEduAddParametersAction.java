package com.jetbrains.python.edu;

import com.intellij.execution.Location;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.EditRunConfigurationsAction;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.editor.EditorGutter;
import com.intellij.openapi.project.Project;
import com.intellij.util.PlatformUtils;
import org.jetbrains.annotations.Nullable;

import java.awt.event.InputEvent;

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
      final ConfigurationContext context = ConfigurationContext.getFromContext(e.getDataContext());
      final Location location = context.getLocation();
      if (location == null) return;
      super.update(e);
      final InputEvent inputEvent = e.getInputEvent();
      final Presentation presentation = e.getPresentation();

      if (inputEvent == null && !(context.getDataContext().getData(PlatformDataKeys.CONTEXT_COMPONENT) instanceof EditorGutter)) {
        presentation.setText("");
      }
      else {
        e.getPresentation().setText("Add Parameters");
      }
    }
  }
}
