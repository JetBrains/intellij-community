// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.resolve

import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil
import com.jetbrains.python.psi.PyQualifiedExpression
import com.jetbrains.python.psi.types.TypeEvalContext
import org.jetbrains.annotations.ApiStatus

/**
 * Forward references resolution for annotations and pyi stubs.
 *
 * @see <a href="PEP-484">https://www.python.org/dev/peps/pep-0484/</a>
 * @see <a href="PEP-563">https://www.python.org/dev/peps/pep-0563/</a>
 */
@ApiStatus.Internal
class PyForwardReferenceResolveProvider : PyReferenceResolveProvider {

  override fun resolveName(element: PyQualifiedExpression, context: TypeEvalContext): List<RatedResolveResult> {
    if (!PyResolveUtil.allowForwardReferences(element)) {
      return emptyList()
    }

    val referencedName = element.referencedName ?: return emptyList()
    val originalOwner = ScopeUtil.getScopeOwner(element)

    return if (originalOwner != null) {
      PyResolveUtil.resolveLocally(originalOwner, referencedName)
        .map { RatedResolveResult(RatedResolveResult.RATE_NORMAL, it) }
    }
    else emptyList()
  }

  override fun allowsForwardOutgoingReferencesInClass(element: PyQualifiedExpression): Boolean {
    return PyResolveUtil.allowForwardReferences(element)
  }

}