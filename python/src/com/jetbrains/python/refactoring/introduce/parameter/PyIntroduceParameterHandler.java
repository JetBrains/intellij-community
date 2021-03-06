// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.refactoring.introduce.parameter;

import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.introduce.inplace.InplaceVariableIntroducer;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.refactoring.PyReplaceExpressionUtil;
import com.jetbrains.python.refactoring.introduce.IntroduceHandler;
import com.jetbrains.python.refactoring.introduce.IntroduceOperation;
import com.jetbrains.python.refactoring.introduce.variable.VariableValidator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.List;

public class PyIntroduceParameterHandler extends IntroduceHandler {
  public PyIntroduceParameterHandler() {
    super(new VariableValidator(), PyBundle.message("refactoring.extract.parameter.dialog.title"));
  }

  @Override
  protected String getHelpId() {
    return "python.reference.introduceParameter";
  }

  @Nullable
  @Override
  protected PsiElement addDeclaration(@NotNull PsiElement expression,
                                      @NotNull PsiElement declaration,
                                      @NotNull IntroduceOperation operation) {
    return doIntroduceParameter(expression, (PyAssignmentStatement)declaration);
  }


  public PsiElement doIntroduceParameter(PsiElement expression, PyAssignmentStatement declaration) {
    PyFunction function = PsiTreeUtil.getParentOfType(expression, PyFunction.class);
    if (function != null && declaration != null) {
      PyParameterList parameterList = function.getParameterList();
      parameterList.addParameter(PyElementGenerator.getInstance(function.getProject()).createParameter(declaration.getText()));
      CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(function);
      return parameterList.findParameterByName(declaration.getTargets()[0].getText());
    }
    return null;
  }

  @Nullable
  @Override
  protected PsiElement replaceExpression(PsiElement expression, PyExpression newExpression, IntroduceOperation operation) {
    return PyReplaceExpressionUtil.replaceExpression(expression, newExpression);
  }

  @Override
  protected boolean isValidIntroduceContext(PsiElement element) {
    if (element != null) {
      if (!isValidPlace(element)) return false;

      return isNotDeclared(element);
    }
    return false;
  }

  private static boolean isNotDeclared(PsiElement element) {
    final ScopeOwner scopeOwner =  ScopeUtil.getScopeOwner(element);
    final boolean[] isValid = {true};

    if (scopeOwner != null) {
      final String name = element instanceof PsiNamedElement? ((PsiNamedElement)element).getName() : element.getText();
      if (name != null && ControlFlowCache.getScope(scopeOwner).containsDeclaration(name)) {
        return false;
      }
      new PyRecursiveElementVisitor() {
        @Override
        public void visitPyReferenceExpression(@NotNull PyReferenceExpression node) {
          super.visitPyReferenceExpression(node);

          final String name = node.getName();
          if (name != null && ControlFlowCache.getScope(scopeOwner).containsDeclaration(name)) {
            isValid[0] = false;
          }
        }
      }.visitElement(element);
    }
    return !isResolvedToParameter(element) && isValid[0];
  }

  private static boolean isValidPlace(PsiElement element) {
    final PyFunction function = PsiTreeUtil.getParentOfType(element, PyFunction.class);
    final PyForPart forPart = PsiTreeUtil.getParentOfType(element, PyForPart.class);
    if (forPart != null) {
      final PyExpression target = forPart.getTarget();
      if (target instanceof PyTargetExpression && element.getText().equals(target.getName()))
        return false;
    }
    final PyStatement nonlocalStatement =
      PsiTreeUtil.getParentOfType(element, PyNonlocalStatement.class, PyGlobalStatement.class);
    final PyStatementList statementList =
      PsiTreeUtil.getParentOfType(element, PyStatementList.class);
    PyImportStatement importStatement = PsiTreeUtil.getParentOfType(element, PyImportStatement.class);
    return nonlocalStatement == null && importStatement == null &&
           statementList != null && function != null;
  }

  private static boolean isResolvedToParameter(PsiElement element) {
    while (element instanceof PyReferenceExpression) {
      final PsiReference reference = element.getReference();
      if (reference != null && reference.resolve() instanceof PyNamedParameter)
        return true;
      element = ((PyReferenceExpression)element).getQualifier();
    }
    return false;
  }

  @Override
  protected void performInplaceIntroduce(IntroduceOperation operation) {
    final PsiElement statement = performRefactoring(operation);
    if (statement instanceof PyNamedParameter) {
      final List<PsiElement> occurrences = operation.getOccurrences();
      final PsiElement occurrence = findOccurrenceUnderCaret(occurrences, operation.getEditor());
      PsiElement elementForCaret = occurrence != null ? occurrence : statement;
      operation.getEditor().getCaretModel().moveToOffset(elementForCaret.getTextRange().getStartOffset());
      final InplaceVariableIntroducer<PsiElement> introducer =
        new PyInplaceParameterIntroducer((PyNamedParameter)statement, operation, occurrences);
      introducer.performInplaceRefactoring(new LinkedHashSet<>(operation.getSuggestedNames()));
    }
  }

  private static class PyInplaceParameterIntroducer extends InplaceVariableIntroducer<PsiElement> {
    private final PyNamedParameter myTarget;

    PyInplaceParameterIntroducer(PyNamedParameter target,
                                 IntroduceOperation operation,
                                 List<PsiElement> occurrences) {
      super(target, operation.getEditor(), operation.getProject(), PyBundle.message("refactoring.introduce.parameter.dialog.title"),
            occurrences.toArray(PsiElement.EMPTY_ARRAY), null);
      myTarget = target;
    }

    @Override
    protected PsiElement checkLocalScope() {
      return myTarget.getContainingFile();
    }
  }

  @Override
  protected String getRefactoringId() {
    return "refactoring.python.introduce.parameter";
  }
}
