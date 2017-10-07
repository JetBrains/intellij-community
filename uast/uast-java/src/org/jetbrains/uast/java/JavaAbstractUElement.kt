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
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.java.internal.JavaUElementWithComments
import org.jetbrains.uast.toUElement

abstract class JavaLazyParentUElement(private val givenParent: UElement?) : UElement {

    override val uastParent: UElement? by lz { givenParent ?: convertParent() }

    protected open fun convertParent(): UElement? =
      getPsiParentForLazyConversion()?.let { JavaConverter.unwrapElements(it).toUElement() }?.also {
          if (it === this) throw IllegalStateException("lazy parent loop for $this")
          if (it.psi != null && it.psi === this.psi) throw IllegalStateException(
            "lazy parent loop: psi ${this.psi}(${this.psi?.javaClass}) for $this of ${this.javaClass}")
      }

    protected open fun getPsiParentForLazyConversion() = this.psi?.parent
}

abstract class JavaAbstractUElement(givenParent: UElement?) : JavaLazyParentUElement(givenParent), JavaUElementWithComments {
    override fun equals(other: Any?): Boolean {
        if (other !is UElement || other.javaClass != this.javaClass) return false
        return if (this.psi != null) this.psi == other.psi else this === other
    }

    override fun hashCode() = psi?.hashCode() ?: System.identityHashCode(this)

    override fun asSourceString(): String {
        return this.psi?.text ?: super<JavaUElementWithComments>.asSourceString()
    }

    override fun toString() = asRenderString()
}

abstract class JavaAbstractUExpression(givenParent: UElement?)  : JavaAbstractUElement(givenParent), UExpression {
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
            else -> it
        }
    }
}
