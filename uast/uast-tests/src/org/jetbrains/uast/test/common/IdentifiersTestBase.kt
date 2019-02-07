/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.uast.test.common

import com.intellij.psi.PsiCodeBlock
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import junit.framework.TestCase
import org.jetbrains.uast.*


fun UFile.asIdentifiers(): String = UElementToParentMap { it.toUElementOfType<UIdentifier>() }.alsoCheck {
  //check uIdentifier is walkable to top (e.g. IDEA-200372)
  TestCase.assertEquals("should be able to reach the file from identifier '${it.text}'",
                        this@asIdentifiers,
                        it.toUElementOfType<UIdentifier>()!!.getParentOfType<UFile>()
  )
}.visitUFileAndGetResult(this)

fun UFile.asRefNames() = UElementToParentMap { it.toUElementOfType<UReferenceExpression>()?.referenceNameElement }
  .visitUFileAndGetResult(this)

open class UElementToParentMap(val retriever: (PsiElement) -> UElement?) : PsiElementVisitor() {

  private val builder = StringBuilder()
  private var level = 0

  private val additionalChecks = mutableListOf<(PsiElement) -> Unit>()

  override fun visitElement(element: PsiElement) {
    val uElement = retriever(element)
    if (uElement != null) {
      builder.append("    ".repeat(level))
      builder.append(uElement.sourcePsiElement!!.text)
      builder.append(" -> ")
      builder.append(uElement.uastParent?.asLogString())
      builder.append(" from ")
      builder.append(renderSource(element))
      builder.appendln()
    }
    if (element is PsiCodeBlock) level++
    element.acceptChildren(this)
    if (element is PsiCodeBlock) level--
  }

  protected open fun renderSource(element: PsiElement): String = element.toString()

  fun alsoCheck(checker: (PsiElement) -> Unit): UElementToParentMap {
    additionalChecks.add(checker)
    return this
  }

  val result: String
    get() = builder.toString()

  open fun visitUFileAndGetResult(uFile: UFile): String {
    (uFile.sourcePsi as PsiFile).accept(this)
    return result
  }

}
