package com.jetbrains.python.inspections.quickfix;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * User: ktisha
 */
public class PyMakeMethodStaticQuickFix implements LocalQuickFix {
  public PyMakeMethodStaticQuickFix() {
  }

  @NotNull
  public String getName() {
    return PyBundle.message("QFIX.NAME.make.static");
  }

  @NonNls
  @NotNull
  public String getFamilyName() {
    return getName();
  }

  public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
    final PsiElement element = descriptor.getPsiElement();
    final PyFunction problemFunction = PsiTreeUtil.getParentOfType(element, PyFunction.class);
    if (problemFunction == null) return;
    if (!PyUtil.deleteParameter(problemFunction, 0)) return;

    PyElementGenerator generator = PyElementGenerator.getInstance(project);
    final PyFunction function =
      generator.createFromText(LanguageLevel.forElement(problemFunction), PyFunction.class, "@staticmethod\ndef foo():\n\tpass");
    final PyDecoratorList decoratorList = function.getDecoratorList();
    assert decoratorList != null;
    final PyDecorator[] decorators = decoratorList.getDecorators();
    final PyDecorator decorator = decorators[0];
    problemFunction.addBefore(decorator, problemFunction.getFirstChild());



  }
}
