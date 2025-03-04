package com.jetbrains.python.validation;

import com.intellij.lang.ASTNode;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyPrefixExpression;
import org.jetbrains.annotations.NotNull;

public final class PyAsyncAwaitAnnotator extends PyAnnotator {
  @Override
  public void visitPyPrefixExpression(@NotNull PyPrefixExpression node) {
    super.visitPyPrefixExpression(node);
    if (node.getOperator() == PyTokenTypes.AWAIT_KEYWORD) {
      var scopeOwner = ScopeUtil.getScopeOwner(node);
      if (!(scopeOwner instanceof PyFunction pyFunction && pyFunction.isAsync())) {
        var annotation = getHolder()
          .newAnnotation(HighlightSeverity.ERROR, PyPsiBundle.message("ANN.await.outside.async.function"))
          .range(node.getFirstChild());
        if (scopeOwner instanceof PyFunction pyFunction) {
            annotation = annotation.newFix(new ConvertIntoAsyncFunctionFix(pyFunction)).registerFix();
        }
        annotation.create();
      }
    }
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
