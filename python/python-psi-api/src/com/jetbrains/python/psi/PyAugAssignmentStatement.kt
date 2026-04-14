// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi

import com.jetbrains.python.ast.PyAstAugAssignmentStatement

interface PyAugAssignmentStatement : PyAstAugAssignmentStatement, PyStatement {
  override val target: PyExpression
    get() = super.value as PyExpression

  override val value: PyExpression?
    get() = super.value as PyExpression?
}
