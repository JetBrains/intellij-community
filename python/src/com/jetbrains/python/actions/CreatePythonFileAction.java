// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.actions;

import com.intellij.ide.actions.CreateFileFromTemplateAction;
import com.intellij.ide.actions.CreateFileFromTemplateDialog;
import com.intellij.ide.actions.NewFileActionWithCategory;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.pyi.PyiFileType;
import org.jetbrains.annotations.NotNull;

public class CreatePythonFileAction extends CreateFileFromTemplateAction implements DumbAware, NewFileActionWithCategory {
  public CreatePythonFileAction() {
    super(PyBundle.messagePointer("action.create.python.file.title"), PyBundle.messagePointer("action.create.python.file.description"), PythonFileType.INSTANCE.getIcon());
  }

  @Override
  protected void buildDialog(Project project, PsiDirectory directory, CreateFileFromTemplateDialog.Builder builder) {
    builder
      .setTitle(PyBundle.message("create.python.file.action.new.python.file"))
      .addKind(PyBundle.message("create.python.file.action.python.file"), PythonFileType.INSTANCE.getIcon(), "Python Script")
      .addKind(PyBundle.message("create.python.file.action.python.unit.test"), PythonFileType.INSTANCE.getIcon(), "Python Unit Test")
      .addKind(PyBundle.message("create.python.file.action.python.stub"), PyiFileType.INSTANCE.getIcon(), "Python Stub");
  }

  @Override
  protected String getActionName(PsiDirectory directory, @NotNull String newName, String templateName) {
    return PyBundle.message("create.python.file.script.action", newName);
  }

  @Override
  public @NotNull String getCategory() {
    return "Python";
  }
}
