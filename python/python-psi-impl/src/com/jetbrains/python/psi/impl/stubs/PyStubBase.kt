// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.impl.stubs

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.stubs.StubBase
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.tree.IElementType
import org.jetbrains.annotations.ApiStatus

/**
 * The base of a Python stub. Its element type is a plain constant of `PyElementTypes`, so the deprecated [getStubType]
 * returns the `PyStubElementTypes` adapter of that constant, for a plugin on the old API.
 */
@ApiStatus.Internal
abstract class PyStubBase<T : PsiElement>(parent: StubElement<*>?, elementType: IElementType?) : StubBase<T>(parent, elementType) {
  @Deprecated("Use getElementType()", ReplaceWith("getElementType()"))
  @Suppress("DEPRECATION")
  override fun getStubType(): IStubElementType<*, *> {
    val elementType = getElementType()
    return (if (elementType != null) PyLegacyStubElementType.forElementType(elementType) else null) ?: super.getStubType()
  }
}
