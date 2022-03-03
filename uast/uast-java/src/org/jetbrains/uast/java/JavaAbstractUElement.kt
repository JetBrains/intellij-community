// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.java

import com.intellij.lang.Language
import com.intellij.lang.java.JavaLanguage
import com.intellij.psi.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.*
import org.jetbrains.uast.java.internal.JavaUElementWithComments

@ApiStatus.Internal
abstract class JavaAbstractUElement(
  givenParent: UElement?
) : JavaUElementWithComments, UElement {

  override fun equals(other: Any?): Boolean {
    if (other !is UElement || other.javaClass != this.javaClass) return false
    return if (this.sourcePsi != null) this.sourcePsi == other.sourcePsi else this === other
  }

  override fun hashCode(): Int = sourcePsi?.hashCode() ?: System.identityHashCode(this)

  override fun asSourceString(): String {
    return this.sourcePsi?.text ?: super<JavaUElementWithComments>.asSourceString()
  }

  override fun toString(): String = asRenderString()

  override val uastParent: UElement? by lz { givenParent ?: convertParent() }

  protected open fun convertParent(): UElement? =
    getPsiParentForLazyConversion()
      ?.let { JavaConverter.unwrapElements(it).toUElement() }
      ?.let { unwrapSwitch(it) }
      ?.let { wrapSingleExpressionLambda(it) }
      ?.also {
        if (it === this) throw IllegalStateException("lazy parent loop for $this")
        if (it.sourcePsi != null && it.sourcePsi === this.sourcePsi)
          throw IllegalStateException("lazy parent loop: sourcePsi ${this.sourcePsi}(${this.sourcePsi?.javaClass}) for $this of ${this.javaClass}")
      }

  protected open fun getPsiParentForLazyConversion(): PsiElement? = this.sourcePsi?.parent

  //explicitly overridden in abstract class to be binary compatible with Kotlin
  override val comments: List<UComment>
    get() = super<JavaUElementWithComments>.comments

  abstract override val sourcePsi: PsiElement?

  override val javaPsi: PsiElement?
    get() = super<JavaUElementWithComments>.javaPsi

  @Suppress("OverridingDeprecatedMember")
  override val psi: PsiElement?
    get() = sourcePsi

}

private fun JavaAbstractUElement.wrapSingleExpressionLambda(uParent: UElement): UElement {
  val sourcePsi = sourcePsi
  return if (uParent is JavaULambdaExpression && sourcePsi is PsiExpression)
    (uParent.body as? UBlockExpression)?.expressions?.singleOrNull() ?: uParent
  else uParent
}

private fun JavaAbstractUElement.unwrapSwitch(uParent: UElement): UElement {
  when (uParent) {
    is UBlockExpression -> {
      val codeBlockParent = uParent.uastParent
      when (codeBlockParent) {

        is JavaUSwitchEntryList -> {
          if (branchHasElement(sourcePsi, codeBlockParent.sourcePsi) { it is PsiSwitchLabelStatementBase }) {
            return codeBlockParent
          }
          val psiElement = sourcePsi ?: return uParent
          return codeBlockParent.findUSwitchEntryForBodyStatementMember(psiElement)?.body ?: return codeBlockParent
        }

        is UExpressionList -> {
          val sourcePsi = codeBlockParent.sourcePsi
          if (sourcePsi is PsiSwitchLabeledRuleStatement)
            (codeBlockParent.uastParent as? JavaUSwitchEntry)?.let { return it.body }
        }

        is JavaUSwitchExpression -> return unwrapSwitch(codeBlockParent)
      }
      return uParent
    }

    is JavaUSwitchEntry -> {
      val parentSourcePsi = uParent.sourcePsi
      if (parentSourcePsi is PsiSwitchLabeledRuleStatement && parentSourcePsi.body?.children?.contains(sourcePsi) == true) {
        val psi = sourcePsi
        return if (psi is PsiExpression && uParent.body.expressions.size == 1)
          DummyYieldExpression(psi, uParent.body)
        else uParent.body
      }
      else
        return uParent
    }

    is USwitchExpression -> {
      val parentPsi = uParent.sourcePsi as PsiSwitchBlock
      return if (this === uParent.body || branchHasElement(sourcePsi, parentPsi) { it === parentPsi.expression })
        uParent
      else
        uParent.body
    }
    else -> return uParent
  }
}

private inline fun branchHasElement(child: PsiElement?, parent: PsiElement?, predicate: (PsiElement) -> Boolean): Boolean {
  var current: PsiElement? = child
  while (current != null && current != parent) {
    if (predicate(current)) return true
    current = current.parent
  }
  return false
}

@ApiStatus.Internal
abstract class JavaAbstractUExpression(
  givenParent: UElement?
) : JavaAbstractUElement(givenParent), UExpression {

  override fun evaluate(): Any? {
    val project = sourcePsi?.project ?: return null
    return JavaPsiFacade.getInstance(project).constantEvaluationHelper.computeConstantExpression(sourcePsi)
  }

  override val uAnnotations: List<UAnnotation>
    get() = emptyList()

  override fun getExpressionType(): PsiType? {
    val expression = sourcePsi as? PsiExpression ?: return null
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

  override val lang: Language
    get() = JavaLanguage.INSTANCE
}
