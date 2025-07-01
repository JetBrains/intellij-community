// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi

import com.intellij.psi.util.findParentOfType
import com.jetbrains.python.ast.PyAstPattern
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.TypeEvalContext
import org.jetbrains.annotations.ApiStatus

interface PyPattern : PyAstPattern, PyTypedElement {
  /**
   * Returns the type that would be captured by this pattern when matching.
   *
   *
   * Unlike other PyTypedElements where getType returns their own type, pattern's getType
   * returns the type that would result from a successful match. For example:
   *
   * ```python
   * class Plant: pass
   * class Animal: pass
   * class Dog(Animal): pass
   *
   * x: Dog | Plant
   * match x:
   * case Animal():
   * # getType returns Dog here, even though the pattern is Animal()
   * ```
   *
   * @see PyCapturePatternImpl.getCaptureType
   */
  override fun getType(context: TypeEvalContext, key: TypeEvalContext.Key): PyType?

  /**
   * Decides if the set of values described by a pattern is suitable
   * to be subtracted (excluded) from a subject type on the negative edge,
   * or if this pattern is too specific.
   */
  @ApiStatus.Experimental
  fun canExcludePatternType(context: TypeEvalContext): Boolean {
    return true
  }
}

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
      return pattern.findParentOfType<PyCaptureContext>()?.getCaptureTypeForChild(pattern, context)
    }
  }
}
