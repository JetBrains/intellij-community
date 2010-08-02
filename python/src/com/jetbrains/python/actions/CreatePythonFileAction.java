package com.jetbrains.python.actions;

import com.intellij.ide.actions.CreateFileFromTemplateAction;
import com.intellij.ide.actions.CreateFileFromTemplateDialog;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;
import com.jetbrains.python.PythonFileType;

/**
 * @author yole
 */
public class CreatePythonFileAction extends CreateFileFromTemplateAction implements DumbAware {
  public CreatePythonFileAction() {
    super("Python File", "Creates a Python file from the specified template", PythonFileType.INSTANCE.getIcon());
  }

  @Override
  protected void buildDialog(Project project, PsiDirectory directory, CreateFileFromTemplateDialog.Builder builder) {
    builder
      .setTitle("New Python file")
      .addKind("Python file", PythonFileType.INSTANCE.getIcon(), "Python Script")
      .addKind("Python unit test", PythonFileType.INSTANCE.getIcon(), "Python Unit Test");
  }

  @Override
  protected String getActionName(PsiDirectory directory, String newName, String templateName) {
    return "Create Python script " + newName; 
  }
}
