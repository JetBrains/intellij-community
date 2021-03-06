// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("UastControlFlowUtils")

package org.jetbrains.uast.util

import org.jetbrains.uast.*

fun UElement.isInFinallyBlock(): Boolean {
  val jumpTarget = (this as? UJumpExpression)?.jumpTarget
  var current: UElement = this
  while (true) {
    val tryStatement = generateSequence(current.uastParent) { it.uastParent }
      .takeWhile { parent -> parent !is UClass && jumpTarget != parent }
      .mapNotNull { it as? UTryExpression }
      .firstOrNull()
      ?: return false

    val finallyBlock = tryStatement.finallyClause
    if (finallyBlock != null && isPsiAncestor(finallyBlock, current)) {
      return true
    }
    current = tryStatement
  }
}
