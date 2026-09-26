// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast.util

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiRecursiveVisitor
import kotlin.reflect.KClass

abstract class IndentedPrintingVisitor(val shouldIndent: (PsiElement) -> Boolean) : PsiElementVisitor(),PsiRecursiveVisitor {

  constructor(vararg kClasses: KClass<*>) : this({ psi -> kClasses.any { it.isInstance(psi) } })

  private val builder = StringBuilder()
  var level: Int = 0
    private set

  override fun visitElement(element: PsiElement) {
    val charSequence = render(element)
    if (charSequence != null) {
      builder.append("    ".repeat(level))
      builder.append(charSequence)
      builder.appendLine()
    }

    val shouldIndent = shouldIndent(element)
    if (shouldIndent) level++
    element.acceptChildren(this)
    if (shouldIndent) level--
  }

  protected abstract fun render(element: PsiElement): CharSequence?

  val result: String
    get() = builder.toString()
}