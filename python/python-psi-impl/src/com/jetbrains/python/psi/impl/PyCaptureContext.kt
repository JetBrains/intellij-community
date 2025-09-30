package com.jetbrains.python.psi.impl

import com.intellij.psi.util.findParentOfType
import com.jetbrains.python.psi.PyElement
import com.jetbrains.python.psi.PyPattern
import com.jetbrains.python.psi.PyUtil
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.TypeEvalContext

interface PyCaptureContext : PyElement {
  fun getCaptureTypeForChild(pattern: PyPattern, context: TypeEvalContext): PyType?

  companion object {
    /**
     * Determines what type this pattern would have if it was a capture pattern (like a bare name or _).
     *
     * In pattern matching, a capture pattern takes on the type of the entire matched expression,
     * regardless of any specific pattern constraints.
     *
     * For example:
     * ```python
     * x: int | str
     * match x:
     *     case a:         # This is a capture pattern
     *         # Here 'a' has type int | str
     *     case str():     # This is a class pattern
     *         # Capture type: int | str (same as what 'case a:' would get)
     *         # Regular getType: str
     *
     * y: int
     * match y:
     *     case str() as a:
     *         # Capture type: int (same as what 'case a:' would get)
     *         # Regular getType: intersect(int, str) (just 'str' for now)
     * ```
     * @see PyPattern#getType(TypeEvalContext, TypeEvalContext.Key)
     */
    @JvmStatic
    fun getCaptureType(pattern: PyPattern, context: TypeEvalContext): PyType? {
      return PyUtil.getNullableParameterizedCachedValue(pattern, context) {
        pattern.findParentOfType<PyCaptureContext>()?.getCaptureTypeForChild(pattern, context)
      }
    }
  }
}