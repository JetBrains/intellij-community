package com.jetbrains.python.actions;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.template.TemplateBuilder;
import com.intellij.codeInsight.template.TemplateBuilderFactory;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;

/**
 * User: catherine
 *
 * QuickFix to create function to unresolved unqualified reference
 */
public class UnresolvedRefCreateFunctionQuickFix implements LocalQuickFix {
  private PyReferenceExpression myElement;

  public UnresolvedRefCreateFunctionQuickFix(PyReferenceExpression element) {
    myElement = element;
  }

  @NotNull
  public String getName() {
    return PyBundle.message("QFIX.unresolved.reference.create.function");
  }

  @NotNull
  public String getFamilyName() {
    return PyBundle.message("INSP.GROUP.python");
  }

  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    if (!CodeInsightUtilBase.preparePsiElementForWrite(myElement)) return;
    PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
    PyFunction function = elementGenerator.createFromText(LanguageLevel.forElement(myElement), PyFunction.class,
                                                          "def " + myElement.getText() + "(a):\n  pass");

    PyStatement statement = PsiTreeUtil.getParentOfType(myElement, PyStatement.class);
    if (statement != null) {
      PsiElement parent = statement.getParent();
      if (parent != null)
        function = (PyFunction)parent.addBefore(function, statement);
    }
    function = CodeInsightUtilBase.forcePsiPostprocessAndRestoreElement(function);
    final TemplateBuilder builder = TemplateBuilderFactory.getInstance().createTemplateBuilder(function);
    builder.replaceElement(function.getParameterList().getParameters()[0], "");
    builder.replaceElement(function.getStatementList().getStatements()[0], "pass");
    builder.run();
  }
}
