package com.jetbrains.python.codeInsight.controlflow

import com.intellij.codeInsight.controlflow.ControlFlowBuilder
import com.intellij.codeInsight.controlflow.impl.InstructionImpl
import com.jetbrains.python.psi.PyWithItem
import com.jetbrains.python.psi.types.TypeEvalContext

/**
 * Each `with` statement surrounds its block with an implicit `try/except`. Depending on the return value
 * of the context manager's `__exit__()` method, the intercepted exception may be suppressed or propagated further.
 *
 * It affects the reachability analysis. Consider the following snippet
 *
 * ```python
 * with manager():
 *     may_raise_exception()
 *     return
 * print("Reachable?")
 * ```
 *
 * The potential exception from `may_raise_exception` jumps over the return statement and invokes `__exit__()`.
 * If then `__exit__()` returns a "truthy" value, suppressing the exception, the subsequent `print` statement becomes reachable.
 *
 * We model this behavior with this special virtual instruction in CFG for a `with` statement.
 * All instructions potentially raising an exception connect to it, while it connects to the next instruction after
 * the `with` statement. During the reachability analysis, we consider the latter edge from this instruction "passable"
 * only if the context manager can suppress exceptions.
 *
 * The rules for detecting if a context manager can suppress exceptions are
 * [described in the typing specification](https://typing.python.org/en/latest/spec/exceptions.html#context-managers).
 *
 * See also the [`__exit__` method documentation in the language reference](https://docs.python.org/3/reference/datamodel.html#object.__exit__).
 *
 * @see PyWithItem.isSuppressingExceptions
 */
class PyWithContextExitInstruction(builder: ControlFlowBuilder, withItem: PyWithItem) : InstructionImpl(builder, withItem) {
  override fun getElementPresentation(): String = "exit context manager: ${element.text}"
  override fun getElement(): PyWithItem = super.getElement() as PyWithItem

  /**
   * While traversing CFG, use this method to know if you should let your traversal consider this node.
   * Usually, you would want it only if the context manager DOES suppress exceptions.
   */
  fun isSuppressingExceptions(context: TypeEvalContext): Boolean = element.isSuppressingExceptions(context)
}