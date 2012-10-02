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
* Date: Nov 30, 2008 6:10:13 AM
*/
public class RenameParameterQuickFix implements LocalQuickFix {
  private final String myNewName;

  public RenameParameterQuickFix(String newName) {
    myNewName = newName;
  }

  public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
    final PsiElement elt = descriptor.getPsiElement();
    if (elt != null && elt instanceof PyNamedParameter && elt.isWritable()) {
      new RenameProcessor(project, elt, myNewName, false, true).run();
    }
  }

  @NotNull
  public String getFamilyName() {
    return "Rename parameter";
  }

  @NotNull
  public String getName() {
    return PyBundle.message("QFIX.rename.parameter.to.$0", myNewName);
  }
}
