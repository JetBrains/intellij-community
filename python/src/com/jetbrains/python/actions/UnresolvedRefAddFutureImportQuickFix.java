package com.jetbrains.python.actions;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;

/**
 * User: catherine
 *
 * QuickFix to remove redundant parentheses from if/while/except statement
 */
public class UnresolvedRefAddFutureImportQuickFix implements LocalQuickFix {
  private PyReferenceExpression myElement;
  public UnresolvedRefAddFutureImportQuickFix(PyReferenceExpression element) {
    myElement = element;
  }

  @NotNull
  public String getName() {
    return PyBundle.message("QFIX.unresolved.reference.add.future");
  }

  @NotNull
  public String getFamilyName() {
    return PyBundle.message("INSP.GROUP.python");
  }

  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PyFile file = (PyFile)myElement.getContainingFile();
    if (!CodeInsightUtilBase.prepareFileForWrite(file)) return;
    PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
    PyFromImportStatement statement = elementGenerator.createFromText(LanguageLevel.forElement(myElement), PyFromImportStatement.class, 
                                                                  "from __future__ import with_statement");
    file.addBefore(statement, file.getStatements().get(0));
  }
}
