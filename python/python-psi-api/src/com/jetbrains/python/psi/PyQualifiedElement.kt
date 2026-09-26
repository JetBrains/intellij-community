// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi

import com.jetbrains.python.ast.PyAstQualifiedElement


/**
 * Represents an optionally qualified element with an associated name. Most of such elements are expressions and extend
 * [PyQualifiedExpression], but one notable exception is [PyAugAssignmentStatement].
 * In practice, using this interface means "any construct referring to something", i.e. having a [com.intellij.psi.PsiReference] on it.
 *
 * Don't mix this interface up with [PyQualifiedNameOwner], which means "some declaration having a name".
 * Typically, this is what [PyQualifiedElement] refers to.
 *
 * @see PyQualifiedNameOwner
 */
interface PyQualifiedElement : PyAstQualifiedElement, PyElement {
  override fun getQualifier(): PyExpression?
}