// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi

import com.jetbrains.python.ast.PyAstAugAssignmentStatement

interface PyAugAssignmentStatement : PyAstAugAssignmentStatement, PyStatement, PyTypedElement, PyQualifiedExpression, PyCallSiteOwner, PyReferenceOwner {
  /**
   * this and [assignmentTarget] both refer to the same element, but for analysis, are split
   *
   *  this refers to the reference before it has been assigned to
   */
  override val target: PyExpression
    get() = super.target as PyExpression

  /**
   * see [target]
   *
   * this refers to the reference after it has been assigned to
   */
  val assignmentTarget: PyTargetExpression

  override val value: PyExpression?
    get() = super.value as PyExpression?

  override fun getQualifier(): PyExpression? {
    return super.getQualifier() as PyExpression?
  }
}
