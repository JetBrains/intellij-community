// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections

import com.jetbrains.python.codeInsight.controlflow.Reachability
import com.jetbrains.python.codeInsight.controlflow.getReachabilityForInspection
import com.jetbrains.python.psi.PyElement
import com.jetbrains.python.psi.PyElementVisitor
import com.jetbrains.python.psi.types.TypeEvalContext
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class PyReachableElementVisitor(
  private val delegate: PyElementVisitor,
  private val context: TypeEvalContext,
) : PyElementVisitor() {
  override fun visitPyElement(node: PyElement) {
    if (node.getReachabilityForInspection(context) == Reachability.REACHABLE) {
      node.accept(delegate)
    }
  }
}