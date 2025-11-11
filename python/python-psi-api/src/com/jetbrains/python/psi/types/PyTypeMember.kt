// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.types

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.jetbrains.python.psi.Property
import com.jetbrains.python.psi.resolve.RatedResolveResult
import org.jetbrains.annotations.ApiStatus

/**
 * Represents a high-level member of a class
 * It can be a simple attribute, a method, a property, a dataclass attribute and so on
 *
 * For a property, one member represents its getter, setter and deleter
 */
@ApiStatus.Experimental
class PyTypeMember @JvmOverloads constructor(
  val mainElement: PsiElement?,
  val type: PyType?,
  val isClassVar: Boolean = false,
  val getter: PsiElement? = mainElement,
  val setter: PsiElement? = mainElement,
  val deleter: PsiElement? = mainElement,
) : RatedResolveResult(0, mainElement) {

  constructor(property: Property, type: PyType?) : this(
    property.getter.valueOrNull(),
    type,
    getter = property.getter.valueOrNull(),
    setter = property.setter.valueOrNull(),
    deleter = property.deleter.valueOrNull(),
  )

  val isWritable: Boolean get() = setter != null
  val isDeletable: Boolean get() = deleter != null

  val name: String? get() = if (mainElement is PsiNamedElement) mainElement.name else null
}