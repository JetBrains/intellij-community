// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi

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
