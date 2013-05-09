package com.jetbrains.python.inspections.quickfix;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.jetbrains.python.PyBundle;
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
  private PyReferenceExpression myElement;
  private String myQualifier;

  public UnresolvedReferenceAddSelfQuickFix(@NotNull final PyReferenceExpression element, @NotNull final String qualifier) {
    myElement = element;
    myQualifier = qualifier;
  }

  @NotNull
  public String getName() {
    return PyBundle.message("QFIX.unresolved.reference", myElement.getText(), myQualifier);
  }

  @NotNull
  public String getFamilyName() {
    return "Add qualifier";
  }

  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    if (!FileModificationService.getInstance().preparePsiElementForWrite(myElement)) return;
    PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
    PyExpression expression = elementGenerator.createExpressionFromText(LanguageLevel.forElement(myElement),
                                                                        myQualifier + "." + myElement.getText());
    myElement.replace(expression);
  }
}
