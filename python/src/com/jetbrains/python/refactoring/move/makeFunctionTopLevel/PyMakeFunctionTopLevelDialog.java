// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.refactoring.move.makeFunctionTopLevel;

import com.intellij.openapi.project.Project;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.refactoring.move.PyBaseMoveDialog;
import com.jetbrains.python.refactoring.move.PyMoveRefactoringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Mikhail Golubev
 */
public class PyMakeFunctionTopLevelDialog extends PyBaseMoveDialog {

  public PyMakeFunctionTopLevelDialog(@NotNull Project project,
                                      @NotNull PyFunction function, 
                                      @NotNull String sourcePath,
                                      @NotNull String destinationPath) {
    super(project, sourcePath, destinationPath);
    final String functionName = PyMoveRefactoringUtil.getPresentableName(function);
    if (function.getContainingClass() != null) {
      setTitle(PyBundle.message("refactoring.make.method.top.level.dialog.title"));
      myDescription.setText(PyBundle.message("refactoring.make.method.top.level.dialog.description", functionName));
    }
    else {
      setTitle(PyBundle.message("refactoring.make.local.function.top.level.dialog.title"));
      myDescription.setText(PyBundle.message("refactoring.make.local.function.top.level.dialog.description", functionName));
    }
    myExtraPanel.setVisible(false);
    init();
  }

  @Override
  protected String getHelpId() {
    return "python.reference.makeFunctionTopLevel";
  }

  @Override
  protected @Nullable String getDimensionServiceKey() {
    return "#com.jetbrains.python.refactoring.move.PyMakeFunctionTopLevelDialog";
  }
}
