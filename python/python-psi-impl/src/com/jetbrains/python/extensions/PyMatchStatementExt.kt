// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.extensions

import com.jetbrains.python.psi.PyMatchStatement
import com.jetbrains.python.psi.types.TypeEvalContext
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
fun PyMatchStatement.isExhaustive(context: TypeEvalContext): Boolean {
  // TODO: placeholder until exhaustive match is introduced
  return caseClauses.any { it.pattern?.isIrrefutable == true }
}