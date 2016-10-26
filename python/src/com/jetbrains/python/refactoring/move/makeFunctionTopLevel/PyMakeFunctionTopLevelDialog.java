/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

  @Nullable
  @Override
  protected String getDimensionServiceKey() {
    return "#com.jetbrains.python.refactoring.move.PyMakeFunctionTopLevelDialog";
  }
}
