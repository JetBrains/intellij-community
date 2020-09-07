// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections.quickfix;

import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyReferenceExpression;
import org.jetbrains.annotations.NotNull;

/**
 * User: catherine
 *
 * QuickFix to add self to unresolved reference
 */
public class UnresolvedReferenceAddSelfQuickFix implements LocalQuickFix, HighPriorityAction {
  private final String myQualifier;
  private final SmartPsiElementPointer<PyReferenceExpression> myElement;

  public UnresolvedReferenceAddSelfQuickFix(@NotNull final PyReferenceExpression element, @NotNull final String qualifier) {
    myElement = SmartPointerManager.createPointer(element);
    myQualifier = qualifier;
  }

  @Override
  @NotNull
  public String getName() {
    final PyReferenceExpression element = myElement.getElement();
    if (element == null) return "";
    return PyPsiBundle.message("QFIX.unresolved.reference", element.getText(), myQualifier);
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return PyPsiBundle.message("QFIX.add.qualifier");
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    final PyReferenceExpression element = myElement.getElement();
    if (element == null) return;
    PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
    PyExpression expression = elementGenerator.createExpressionFromText(LanguageLevel.forElement(element),
                                                                        myQualifier + "." + element.getText());
    element.replace(expression);
  }
}
