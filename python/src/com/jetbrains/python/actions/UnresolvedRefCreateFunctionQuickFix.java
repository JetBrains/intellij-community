package com.jetbrains.python.actions;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.template.TemplateBuilder;
import com.intellij.codeInsight.template.TemplateBuilderFactory;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.ParamHelper;
import com.jetbrains.python.psi.impl.PyFunctionBuilder;
import org.jetbrains.annotations.NotNull;

/**
 * User: catherine
 *
 * QuickFix to create function to unresolved unqualified reference
 */
public class UnresolvedRefCreateFunctionQuickFix implements LocalQuickFix {
  private PyCallExpression myElement;
  private PyReferenceExpression myReference;
  public UnresolvedRefCreateFunctionQuickFix(PyCallExpression element, PyReferenceExpression reference) {
    myElement = element;
    myReference = reference;
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

    PyFunctionBuilder functionBuilder = new PyFunctionBuilder(myReference.getText());

    for (PyExpression param : myElement.getArgumentList().getArguments()) {
      if (param instanceof PyKeywordArgument) {
        functionBuilder.parameter(((PyKeywordArgument)param).getKeyword());
      }
      else if (param instanceof PyReferenceExpression) {
        PyReferenceExpression refex = (PyReferenceExpression)param;
        functionBuilder.parameter(refex.getReferencedName());
      }
      else {
        functionBuilder.parameter("param");
      }
    }
    PyFunction function = functionBuilder.buildFunction(project);
    PyFunction parentFunction = PsiTreeUtil.getTopmostParentOfType(myElement, PyFunction.class);
    if (parentFunction != null ) {
      PsiFile file = myElement.getContainingFile();
      function = (PyFunction)file.addBefore(function, parentFunction);
    } else {
      PyStatement statement = PsiTreeUtil.getParentOfType(myElement, PyStatement.class);
      if (statement != null) {
        PsiElement parent = statement.getParent();
        if (parent != null)
          function = (PyFunction)parent.addBefore(function, statement);
      }
    }
    function = CodeInsightUtilBase.forcePsiPostprocessAndRestoreElement(function);
    final TemplateBuilder builder = TemplateBuilderFactory.getInstance().createTemplateBuilder(function);
    ParamHelper.walkDownParamArray(
      function.getParameterList().getParameters(),
      new ParamHelper.ParamVisitor() {
        public void visitNamedParameter(PyNamedParameter param, boolean first, boolean last) {
          builder.replaceElement(param, param.getName());
        }
      }
    );
    builder.replaceElement(function.getStatementList(), "pass");
    builder.run();
  }
}
