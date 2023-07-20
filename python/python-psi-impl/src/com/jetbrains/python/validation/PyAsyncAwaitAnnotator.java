package com.jetbrains.python.validation;

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
        markError(node.getFirstChild(), PyPsiBundle.message("ANN.await.outside.async.function"));
      }
    }
  }
}
