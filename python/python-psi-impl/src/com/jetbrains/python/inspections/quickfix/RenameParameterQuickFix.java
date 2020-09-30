// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections.quickfix;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.PythonUiService;
import com.jetbrains.python.psi.PyNamedParameter;
import org.jetbrains.annotations.NotNull;

/**
 * Parameter renaming.
* User: dcheryasov
*/
public class RenameParameterQuickFix implements LocalQuickFix {
  private final String myNewName;

  public RenameParameterQuickFix(String newName) {
    myNewName = newName;
  }

  @Override
  public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
    final PsiElement elt = descriptor.getPsiElement();
    if (elt instanceof PyNamedParameter) {
      PythonUiService.getInstance().runRenameProcessor(project, elt, myNewName, false, true);
    }
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return PyPsiBundle.message("QFIX.NAME.rename.parameter");
  }

  @Override
  @NotNull
  public String getName() {
    return PyPsiBundle.message("QFIX.rename.parameter", myNewName);
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
