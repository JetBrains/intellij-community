package com.jetbrains.python.validation;

import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.lang.ASTNode;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonRuntimeService;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;

public final class PyAsyncAwaitAnnotator extends PyAnnotator {
  private static boolean isAsyncAllowed(ScopeOwner scopeOwner) {
    // Async functions are allowed to contain "await", "async with" and "async for"
    if (scopeOwner instanceof PyFunction pyFunction && pyFunction.isAsync()) return true;

    // Top-level expressions in the Python console are allowed to contain "await", "async with" and "async for"
    if (scopeOwner instanceof PyExpressionCodeFragment && PythonRuntimeService.getInstance().isInPydevConsole(scopeOwner)) return true;

    return false;
  }


  private void createError(@NotNull PsiElement node, ScopeOwner scopeOwner, @InspectionMessage @NotNull String message) {
    var annotation = getHolder()
      .newAnnotation(HighlightSeverity.ERROR, message)
      .range(node);
    if (scopeOwner instanceof PyFunction pyFunction) {
      annotation = annotation.newFix(new ConvertIntoAsyncFunctionFix(pyFunction)).registerFix();
    }
    annotation.create();
  }

  private void checkComprehension(@NotNull PyComprehensionElement node) {
    var asyncNode = node.getNode().findChildByType(PyTokenTypes.ASYNC_KEYWORD);
    if (asyncNode == null) return;

    var scopeOwner = ScopeUtil.getScopeOwner(node);
    if (isAsyncAllowed(scopeOwner)) return;

    createError((PsiElement)asyncNode, scopeOwner, PyPsiBundle.message("ANN.async.for.outside.function"));
  }

  @Override
  public void visitPyPrefixExpression(@NotNull PyPrefixExpression node) {
    super.visitPyPrefixExpression(node);

    if (node.getOperator() == PyTokenTypes.AWAIT_KEYWORD) {
      var scopeOwner = ScopeUtil.getScopeOwner(node);
      if (isAsyncAllowed(scopeOwner)) return;

      createError(node.getFirstChild(), scopeOwner, PyPsiBundle.message("ANN.await.outside.async.function"));
    }
  }

  @Override
  public void visitPyForStatement(@NotNull PyForStatement node) {
    super.visitPyForStatement(node);
    if (!node.isAsync()) return;

    var scopeOwner = ScopeUtil.getScopeOwner(node);
    if (isAsyncAllowed(scopeOwner)) return;

    createError(node.getFirstChild(), scopeOwner, PyPsiBundle.message("ANN.async.for.outside.function"));
  }

  @Override
  public void visitPyWithStatement(@NotNull PyWithStatement node) {
    super.visitPyWithStatement(node);
    if (!node.isAsync()) return;

    var scopeOwner = ScopeUtil.getScopeOwner(node);
    if (isAsyncAllowed(scopeOwner)) return;

    createError(node.getFirstChild(), scopeOwner, PyPsiBundle.message("ANN.async.with.outside.function"));
  }

  @Override
  public void visitPyListCompExpression(@NotNull PyListCompExpression node) {
    super.visitPyListCompExpression(node);
    checkComprehension(node);
  }

  @Override
  public void visitPyDictCompExpression(@NotNull PyDictCompExpression node) {
    super.visitPyDictCompExpression(node);
    checkComprehension(node);
  }

  @Override
  public void visitPySetCompExpression(@NotNull PySetCompExpression node) {
    super.visitPySetCompExpression(node);
    checkComprehension(node);
  }

  private static class ConvertIntoAsyncFunctionFix extends PsiUpdateModCommandAction<PyFunction> {
    protected ConvertIntoAsyncFunctionFix(@NotNull PyFunction element) {
      super(element);
    }

    @Override
    protected void invoke(@NotNull ActionContext context, @NotNull PyFunction element, @NotNull ModPsiUpdater updater) {
      ASTNode defKeyword = element.getNode().findChildByType(PyTokenTypes.DEF_KEYWORD);
      element.getNode().addLeaf(PyTokenTypes.ASYNC_KEYWORD, "async ", defKeyword);
    }

    @Override
    public @NotNull String getFamilyName() {
      return PyPsiBundle.message("QFIX.convert.into.async.function");
    }
  }
}
