/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.uast.java

import com.intellij.psi.*
import org.jetbrains.uast.*
import org.jetbrains.uast.java.internal.JavaUElementWithComments


abstract class JavaAbstractUElement(givenParent: UElement?) : JavaUElementWithComments, JvmDeclarationUElement {

  @Suppress("unused") // Used in Kotlin 1.2, to be removed in 2018.1
  @Deprecated("use JavaAbstractUElement(givenParent)", ReplaceWith("JavaAbstractUElement(givenParent)"))
  constructor() : this(null)

  override fun equals(other: Any?): Boolean {
    if (other !is UElement || other.javaClass != this.javaClass) return false
    return if (this.psi != null) this.psi == other.psi else this === other
  }

  override fun hashCode(): Int = psi?.hashCode() ?: System.identityHashCode(this)

  override fun asSourceString(): String {
    return this.psi?.text ?: super<JavaUElementWithComments>.asSourceString()
  }

  override fun toString(): String = asRenderString()

  override val uastParent: UElement? by lz { givenParent ?: convertParent() }

  protected open fun convertParent(): UElement? =
    getPsiParentForLazyConversion()
      ?.let { JavaConverter.unwrapElements(it).toUElement() }
      ?.let { unwrapSwitch(it) }
      ?.also {
        if (it === this) throw IllegalStateException("lazy parent loop for $this")
        if (it.psi != null && it.psi === this.psi)
          throw IllegalStateException("lazy parent loop: psi ${this.psi}(${this.psi?.javaClass}) for $this of ${this.javaClass}")
      }

  protected open fun getPsiParentForLazyConversion(): PsiElement? = this.psi?.parent

  //explicitly overridden in abstract class to be binary compatible with Kotlin
  override val comments: List<UComment>
    get() = super<JavaUElementWithComments>.comments
  override val sourcePsi: PsiElement?
    get() = super<JavaUElementWithComments>.sourcePsi
  override val javaPsi: PsiElement?
    get() = super<JavaUElementWithComments>.javaPsi

}

private fun JavaAbstractUElement.unwrapSwitch(uParent: UElement): UElement {
  when (uParent) {
    is UBlockExpression -> {
      val codeBlockParent = uParent.uastParent
      when (codeBlockParent) {

        is JavaUBlockExpression -> {
          val sourcePsi = codeBlockParent.sourcePsi
          if (sourcePsi is PsiBlockStatement && sourcePsi.parent is PsiSwitchLabeledRuleStatement)
            (codeBlockParent.uastParent as? JavaUSwitchEntry)?.let { return it.body }
        }

        is JavaUSwitchEntryList -> {
          if (branchHasElement(psi, codeBlockParent.psi) { it is PsiSwitchLabelStatementBase }) {
            return codeBlockParent
          }
          val psiElement = psi ?: return uParent
          return codeBlockParent.findUSwitchEntryForBodyStatementMember(psiElement)?.body ?: return codeBlockParent
        }

        is JavaUSwitchExpression -> return unwrapSwitch(codeBlockParent)
      }
      return uParent
    }

    is JavaUSwitchEntry -> {
      val parentSourcePsi = uParent.sourcePsi
      if (parentSourcePsi is PsiSwitchLabeledRuleStatement && parentSourcePsi.body?.children?.contains(psi) == true) {
        val psi = psi
        return if (psi is PsiExpression && uParent.body.expressions.size == 1)
          DummyUBreakExpression(psi, uParent.body)
        else uParent.body
      }
      else
        return uParent
    }

    is USwitchExpression -> {
      val parentPsi = uParent.psi as PsiSwitchBlock
      return if (this === uParent.body || branchHasElement(psi, parentPsi) { it === parentPsi.expression })
        uParent
      else
        uParent.body
    }
    else -> return uParent
  }
}

private inline fun branchHasElement(child: PsiElement?, parent: PsiElement?, predicate: (PsiElement) -> Boolean): Boolean {
  var current: PsiElement? = child;
  while (current != null && current != parent) {
    if (predicate(current)) return true
    current = current.parent
  }
  return false
}

abstract class JavaAbstractUExpression(givenParent: UElement?) : JavaAbstractUElement(givenParent), UExpression {

  @Suppress("unused") // Used in Kotlin 1.2, to be removed in 2018.1
  @Deprecated("use JavaAbstractUExpression(givenParent)", ReplaceWith("JavaAbstractUExpression(givenParent)"))
  constructor() : this(null)

  override fun evaluate(): Any? {
    val project = psi?.project ?: return null
    return JavaPsiFacade.getInstance(project).constantEvaluationHelper.computeConstantExpression(psi)
  }

  override val annotations: List<UAnnotation>
    get() = emptyList()

  override fun getExpressionType(): PsiType? {
    val expression = psi as? PsiExpression ?: return null
    return expression.type
  }

  override fun getPsiParentForLazyConversion(): PsiElement? = super.getPsiParentForLazyConversion()?.let {
    when (it) {
      is PsiResourceExpression -> it.parent
      is PsiReferenceExpression -> (it.parent as? PsiMethodCallExpression) ?: it
      else -> it
    }
  }

  override fun convertParent(): UElement? = super.convertParent().let { uParent ->
    when (uParent) {
      is UAnonymousClass -> uParent.uastParent
      else -> uParent
    }
  }.let(this::unwrapCompositeQualifiedReference)
}
