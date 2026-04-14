// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi

import com.jetbrains.python.ast.PyAstQualifiedExpression

/**
 * Represents a qualified expression, that is, of "a.b.c..." sort.
 */
interface PyQualifiedExpression : PyAstQualifiedExpression, PyExpression {
  override fun getQualifier(): PyExpression?
}
