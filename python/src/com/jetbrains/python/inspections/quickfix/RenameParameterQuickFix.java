// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections.quickfix;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.rename.RenameProcessor;
import com.jetbrains.python.PyBundle;
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
      new RenameProcessor(project, elt, myNewName, false, true).run();
    }
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return "Rename parameter";
  }

  @Override
  @NotNull
  public String getName() {
    return PyBundle.message("QFIX.rename.parameter.to.$0", myNewName);
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
