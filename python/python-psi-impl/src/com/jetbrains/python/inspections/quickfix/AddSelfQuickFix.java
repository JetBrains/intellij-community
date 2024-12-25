// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections.quickfix;

import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyParameterList;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * Insert 'self' in a method that lacks any arguments
 */
public class AddSelfQuickFix extends PsiUpdateModCommandQuickFix {
  private final String myParamName;

  public AddSelfQuickFix(String paramName) {
    myParamName = paramName;
  }

  @Override
  public @NotNull String getName() {
    return PyPsiBundle.message("QFIX.add.parameter.self", myParamName);
  }

  @Override
  public @NonNls @NotNull String getFamilyName() {
    return "Add parameter";
  }

  @Override
  public void applyFix(final @NotNull Project project, final @NotNull PsiElement element, final @NotNull ModPsiUpdater updater) {
    if (element instanceof PyParameterList parameterList) {
      PyNamedParameter newParameter = PyElementGenerator.getInstance(project).createParameter(myParamName);
      parameterList.addParameter(newParameter);
    }
  }
}
