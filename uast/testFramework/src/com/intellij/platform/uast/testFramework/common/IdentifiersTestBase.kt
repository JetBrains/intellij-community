// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.uast.testFramework.common

import com.intellij.psi.PsiCodeBlock
import com.intellij.psi.PsiElement
import junit.framework.TestCase
import org.jetbrains.uast.*
import org.jetbrains.uast.util.IndentedPrintingVisitor
import kotlin.reflect.KClass


fun UFile.asIdentifiers(): String = UElementToParentMap { it.toUElementOfType<UIdentifier>() }.alsoCheck {
  //check uIdentifier is walkable to top (e.g. IDEA-200372)
  TestCase.assertEquals("should be able to reach the file from identifier '${it.text}'",
                        this@asIdentifiers,
                        it.toUElementOfType<UIdentifier>()!!.getParentOfType<UFile>()
  )
}.visitUFileAndGetResult(this)

fun UFile.asRefNames(): String = UElementToParentMap { it.toUElementOfType<UReferenceExpression>()?.referenceNameElement }
  .visitUFileAndGetResult(this)

open class UElementToParentMap(shouldIndent: (PsiElement) -> Boolean,
                               val retriever: (PsiElement) -> UElement?) : IndentedPrintingVisitor(shouldIndent) {

  constructor(kClass: KClass<*>, retriever: (PsiElement) -> UElement?) : this({ kClass.isInstance(it) }, retriever)

  constructor(retriever: (PsiElement) -> UElement?) : this(PsiCodeBlock::class, retriever)

  private val additionalChecks = mutableListOf<(PsiElement) -> Unit>()

  override fun render(element: PsiElement): CharSequence? = retriever(element)?.let { uElement ->
    StringBuilder().apply {
      append(uElement.sourcePsiElement!!.text)
      append(" -> ")
      append(uElement.uastParent?.asLogString())
      append(" from ")
      append(renderSource(element))
    }
  }

  protected open fun renderSource(element: PsiElement): String = element.toString()

  fun alsoCheck(checker: (PsiElement) -> Unit): UElementToParentMap {
    additionalChecks.add(checker)
    return this
  }
}

fun IndentedPrintingVisitor.visitUFileAndGetResult(uFile: UFile): String {
  uFile.sourcePsi.accept(this)
  return result
}
