package com.jetbrains.python.inspections.quickfix;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFromImportStatement;
import org.jetbrains.annotations.NotNull;

/**
 * User: catherine
 *
 * QuickFix to add 'from __future__ import with_statement'' if python version is less than 2.6
 */
public class UnresolvedRefAddFutureImportQuickFix implements LocalQuickFix {
  @NotNull
  public String getName() {
    return PyBundle.message("QFIX.unresolved.reference.add.future");
  }

  @NotNull
  public String getFamilyName() {
    return getName();
  }

  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PsiElement element = descriptor.getPsiElement();
    PyFile file = (PyFile)element.getContainingFile();
    if (!FileModificationService.getInstance().prepareFileForWrite(file)) return;
    PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
    PyFromImportStatement statement = elementGenerator.createFromText(LanguageLevel.forElement(element), PyFromImportStatement.class,
                                                                  "from __future__ import with_statement");
    file.addBefore(statement, file.getStatements().get(0));
  }
}
