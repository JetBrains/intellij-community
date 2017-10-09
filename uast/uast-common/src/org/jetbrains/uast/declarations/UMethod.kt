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
package org.jetbrains.uast

import com.intellij.psi.PsiAnnotationMethod
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.internal.acceptList
import org.jetbrains.uast.internal.log
import org.jetbrains.uast.visitor.UastTypedVisitor
import org.jetbrains.uast.visitor.UastVisitor

/**
 * A method visitor to be used in [UastVisitor].
 */
interface UMethod : UDeclaration, PsiMethod {
    override val psi: PsiMethod

    /**
     * Returns the body expression (which can be also a [UBlockExpression]).
     */
    val uastBody: UExpression?

    /**
     * Returns the method parameters.
     */
    val uastParameters: List<UParameter>

    /**
     * Returns true, if the method overrides a method of a super class.
     */
    val isOverride: Boolean

    @Deprecated("Use uastBody instead.", ReplaceWith("uastBody"))
    override fun getBody() = psi.body

    override fun accept(visitor: UastVisitor) {
        if (visitor.visitMethod(this)) return
        annotations.acceptList(visitor)
        uastParameters.acceptList(visitor)
        uastBody?.accept(visitor)
        visitor.afterVisitMethod(this)
    }

    override fun asRenderString() = buildString {
        if (annotations.isNotEmpty()) {
            annotations.joinTo(buffer = this, separator = "\n", postfix = "\n", transform = UAnnotation::asRenderString)
        }

        append(psi.renderModifiers())
        append("fun ").append(name)

        uastParameters.joinTo(this, prefix = "(", postfix = ")") { parameter ->
            val annotationsText = if (parameter.annotations.isNotEmpty())
                parameter.annotations.joinToString(separator = " ", postfix = " ") { it.asRenderString() }
            else
                ""
            annotationsText + parameter.name + ": " + parameter.type.canonicalText
        }

        psi.returnType?.let { append(" : " + it.canonicalText) }

        val body = uastBody
        append(when (body) {
            is UBlockExpression -> " " + body.asRenderString()
            else -> " = " + ((body ?: UastEmptyExpression).asRenderString())
        })
    }

    override fun <D, R> accept(visitor: UastTypedVisitor<D, R>, data: D) =
            visitor.visitMethod(this, data)

    override fun asLogString() = log("name = $name")
}

interface UAnnotationMethod : UMethod, PsiAnnotationMethod {
    override val psi: PsiAnnotationMethod

    /**
     * Returns the default value of this annotation method.
     */
    val uastDefaultValue: UExpression?

    override fun getDefaultValue() = psi.defaultValue

    override fun accept(visitor: UastVisitor) {
        if (visitor.visitMethod(this)) return
        annotations.acceptList(visitor)
        uastParameters.acceptList(visitor)
        uastBody?.accept(visitor)
        uastDefaultValue?.accept(visitor)
        visitor.afterVisitMethod(this)
    }

    override fun asLogString() = log("name = $name")
}