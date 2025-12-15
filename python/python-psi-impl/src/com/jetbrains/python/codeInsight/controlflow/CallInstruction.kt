package com.jetbrains.python.codeInsight.controlflow

import com.intellij.codeInsight.controlflow.ControlFlowBuilder
import com.intellij.codeInsight.controlflow.impl.InstructionImpl
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyKnownDecoratorUtil
import com.jetbrains.python.psi.PyUtil
import com.jetbrains.python.psi.impl.PyCallExpressionHelper
import com.jetbrains.python.psi.impl.PyFunctionImpl
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.types.PyNeverType
import com.jetbrains.python.psi.types.PyTypeUtil
import com.jetbrains.python.psi.types.TypeEvalContext
import org.jetbrains.annotations.ApiStatus

class CallInstruction(builder: ControlFlowBuilder, call: PyCallExpression) : InstructionImpl(builder, call) {
  override fun getElement(): PyCallExpression {
    return super.getElement() as PyCallExpression
  }

  fun isNoReturnCall(context: TypeEvalContext): Boolean = isNoReturnCall(context, true)

  /**
   * Checks if the call never returns normally.
   *
   * An annotated `NoReturn` or `Never` return type of the callee makes the call no-return.
   * If [inspectCalleeBody] is true, a single unannotated callee is no-return also when its body has no return points.
   * The analysis of that body does not inspect the bodies of its own callees. Thus, it does not go recursively through the call graph.
   */
  @ApiStatus.Internal
  fun isNoReturnCall(context: TypeEvalContext, inspectCalleeBody: Boolean): Boolean {
    val callees = element.multiResolveCalleeFunction(PyResolveContext.defaultContext(context))
    if (callees.size == 1) {
      val pyFunction = callees.single() as? PyFunction ?: return false
      if (hasReturnTypeAnnotation(pyFunction)) {
        return context.getReturnType(pyFunction) is PyNeverType
      }
      return inspectCalleeBody && hasNoReturnPoints(pyFunction, context)
    }
    if (callees.isNotEmpty()) return false

    // The callee is not a plain function, e.g. an attribute annotated with `Callable[...]`
    // or an instance whose `__call__` is such an attribute. Its return type always comes from an annotation.
    val callee = element.callee ?: return false
    val calleeType = PyCallExpressionHelper.getCalleeType(callee, PyResolveContext.defaultContext(context))
    val signatures = PyTypeUtil.getCallableItems(calleeType)
    return signatures.isNotEmpty() && signatures.all { it.getReturnType(context) is PyNeverType }
  }
}

private fun hasReturnTypeAnnotation(function: PyFunction): Boolean {
  return function.annotation != null || function.typeCommentAnnotation != null
}

/**
 * Checks if the body of an unannotated [function] cannot complete normally, e.g. because it always raises an exception.
 *
 * Some functions are not analyzed:
 * - a function in a file that [context] does not allow to load as AST, such as a function from another file in the code analysis;
 * - a generator or an `async` function, because the call only creates a generator or a coroutine;
 * - an abstract method, or a method whose body only raises `NotImplementedError`, because a subclass overrides it;
 * - a function with an unknown decorator, because the decorator can replace the function.
 */
private fun hasNoReturnPoints(function: PyFunction, context: TypeEvalContext): Boolean {
  return context.maySwitchToAST(function) &&
         !function.isGenerator &&
         !function.isAsync &&
         !function.onlyRaisesNotImplementedError() &&
         !PyKnownDecoratorUtil.hasUnknownDecorator(function, context) &&
         !PyKnownDecoratorUtil.hasAbstractDecorator(function, context) &&
         PyUtil.getParameterizedCachedValue(function, context) { PyFunctionImpl.collectReturnPoints(function, it, false).isEmpty() }
}
