// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.intentions;

import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.codeInsight.controlflow.ControlFlow;
import com.intellij.codeInsight.controlflow.Instruction;
import com.intellij.codeInsight.template.Expression;
import com.intellij.codeInsight.template.TemplateBuilder;
import com.intellij.codeInsight.template.TemplateBuilderFactory;
import com.intellij.codeInsight.template.impl.ConstantNode;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.codeInsight.codeFragment.PyCodeFragmentUtil;
import com.jetbrains.python.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyFunctionBuilder;
import com.jetbrains.python.refactoring.introduce.IntroduceValidator;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

/**
 * User: catherine
 * Intention to convert lambda to function
 */
public final class PyConvertLambdaToFunctionIntention extends PyBaseIntentionAction {

  @Override
  public @NotNull String getFamilyName() {
    return PyPsiBundle.message("INTN.convert.lambda.to.function");
  }

  @Override
  public @NotNull String getText() {
    return PyPsiBundle.message("INTN.convert.lambda.to.function");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile psiFile) {
    if (!(psiFile instanceof PyFile)) {
      return false;
    }

    PyLambdaExpression lambdaExpression = PsiTreeUtil.getParentOfType(psiFile.findElementAt(editor.getCaretModel().getOffset()), PyLambdaExpression.class);
    if (lambdaExpression != null) {
      if (lambdaExpression.getBody() != null) {
        final ControlFlow flow = ControlFlowCache.getControlFlow(lambdaExpression);
        final List<Instruction> graph = Arrays.asList(flow.getInstructions());
        final List<PsiElement> elements = PyCodeFragmentUtil.getInputElements(graph, graph);
        if (!elements.isEmpty()) return false;
        return true;
      }
    }
    return false;
  }

  @Override
  public void doInvoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    PyLambdaExpression lambdaExpression = PsiTreeUtil.getParentOfType(file.findElementAt(editor.getCaretModel().getOffset()), PyLambdaExpression.class);
    if (lambdaExpression != null) {
      String name = "function";
      while (IntroduceValidator.isDefinedInScope(name, lambdaExpression)) {
        name += "1";
      }

      PsiElement parent = lambdaExpression.getParent();
      if (parent instanceof PyAssignmentStatement) {
        name = ((PyAssignmentStatement)parent).getLeftHandSideExpression().getText();
      }

      if (name.isEmpty()) return;
      PyExpression body = lambdaExpression.getBody();
      PyFunctionBuilder functionBuilder = new PyFunctionBuilder(name, lambdaExpression);
      for (PyParameter param : lambdaExpression.getParameterList().getParameters()) {
        functionBuilder.parameter(param.getText());
      }
      functionBuilder.statement("return " + body.getText());
      PyFunction function = functionBuilder.buildFunction();

      final PyStatement statement = PsiTreeUtil.getParentOfType(lambdaExpression,
                                                                 PyStatement.class);
      if (statement != null) {
        final PsiElement statementParent = statement.getParent();
        if (statementParent != null)
          function = (PyFunction)statementParent.addBefore(function, statement);
      }

      function = CodeInsightUtilCore
        .forcePsiPostprocessAndRestoreElement(function);

      if (parent instanceof PyAssignmentStatement) {
        parent.delete();
      }
      else {
        PsiFile parentScope = lambdaExpression.getContainingFile();
        final TemplateBuilder builder = TemplateBuilderFactory.getInstance().createTemplateBuilder(parentScope);
        PsiElement functionName = function.getNameIdentifier();
        functionName = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(functionName);
        lambdaExpression = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(lambdaExpression);

        Expression refExpr = new ConstantNode(name);

        builder.replaceElement(lambdaExpression, name, refExpr, true);
        builder.replaceElement(functionName, name, name, false);

        editor.getCaretModel().moveToOffset(parentScope.getTextRange().getStartOffset());

        builder.run(editor, true);
      }
    }
  }
}
