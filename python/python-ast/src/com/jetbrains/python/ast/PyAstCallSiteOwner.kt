package com.jetbrains.python.ast

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface PyAstCallSiteOwner {
  /**
   * Returns an expression that is treated as a receiver for this explicit or implicit (read, operator) call.
   *
   *
   * For most operator expressions it returns the result of `getOperator()` since it naturally represents
   * the object on which a special magic method is called. However for binary expressions that additionally
   * can be reversible such as `__add__` and `__radd__` it also takes into account name of the
   * actual callee method and chained comparisons order if any.
   *
   * @param resolvedCallee optional callee corresponding to the call. Without it the receiver is deduced purely syntactically.
   */
  fun getReceiver(resolvedCallee: PyAstCallable?): PyAstExpression?

  fun getArguments(resolvedCallee: PyAstCallable?): List<PyAstExpression?>
}
