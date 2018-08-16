// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections.quickfix;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyParameterList;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * Insert 'self' in a method that lacks any arguments
 * User: dcheryasov
 */
public class AddSelfQuickFix implements LocalQuickFix {
  private final String myParamName;

  public AddSelfQuickFix(String paramName) {
    myParamName = paramName;
  }

  @Override
  @NotNull
  public String getName() {
    return PyBundle.message("QFIX.add.parameter.self", myParamName);
  }

  @Override
  @NonNls
  @NotNull
  public String getFamilyName() {
    return "Add parameter";
  }

  @Override
  public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
    PsiElement element = descriptor.getPsiElement();
    if (element instanceof PyParameterList) {
      final PyParameterList parameterList = (PyParameterList)element;
      PyNamedParameter newParameter = PyElementGenerator.getInstance(project).createParameter(myParamName);
      parameterList.addParameter(newParameter);
    }
  }
}
