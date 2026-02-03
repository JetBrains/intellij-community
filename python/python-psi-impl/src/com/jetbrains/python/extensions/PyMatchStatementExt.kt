package com.jetbrains.python.extensions

import com.jetbrains.python.psi.PyMatchStatement
import com.jetbrains.python.psi.types.TypeEvalContext

fun PyMatchStatement.isExhaustive(context: TypeEvalContext): Boolean {
  // TODO: placeholder until exhaustive match is introduced
  return caseClauses.any { it.pattern?.isIrrefutable == true }
}