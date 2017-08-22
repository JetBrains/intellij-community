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

import com.intellij.psi.*
import org.jetbrains.uast.internal.acceptList
import org.jetbrains.uast.internal.log
import org.jetbrains.uast.visitor.UastTypedVisitor
import org.jetbrains.uast.visitor.UastVisitor

/**
 * A variable wrapper to be used in [UastVisitor].
 */
interface UVariable : UDeclaration, PsiVariable {
    override val psi: PsiVariable

    /**
     * Returns the variable initializer or the parameter default value, or null if the variable has not an initializer.
     */
    val uastInitializer: UExpression?

    /**
     * Returns variable type reference.
     */
    val typeReference: UTypeReferenceExpression?

    override fun accept(visitor: UastVisitor) {
        if (visitor.visitVariable(this)) return
        visitContents(visitor)
        visitor.afterVisitVariable(this)
    }

    override fun <D, R> accept(visitor: UastTypedVisitor<D, R>, data: D) =
            visitor.visitVariable(this, data)

    @Deprecated("Use uastInitializer instead.", ReplaceWith("uastInitializer"))
    override fun getInitializer() = psi.initializer

    override fun asLogString() = log("name = $name")

    override fun asRenderString() = buildString {
        if (annotations.isNotEmpty()) {
            annotations.joinTo(this, separator = " ", postfix = " ") { it.asRenderString() }
        }
        append(psi.renderModifiers())
        append("var ").append(psi.name).append(": ").append(psi.type.getCanonicalText(false))
        uastInitializer?.let { initializer -> append(" = " + initializer.asRenderString()) }
    }
}

private fun UVariable.visitContents(visitor: UastVisitor) {
    annotations.acceptList(visitor)
    uastInitializer?.accept(visitor)
}

interface UParameter : UVariable, PsiParameter {
    override val psi: PsiParameter

    override fun asLogString() = log("name = $name")

    override fun accept(visitor: UastVisitor) {
        if (visitor.visitParameter(this)) return
        visitContents(visitor)
        visitor.afterVisitParameter(this)
    }

    override fun <D, R> accept(visitor: UastTypedVisitor<D, R>, data: D) = visitor.visitParameter(this, data)
}

interface UField : UVariable, PsiField {
    override val psi: PsiField

    override fun asLogString() = log("name = $name")

    override fun accept(visitor: UastVisitor) {
        if (visitor.visitField(this)) return
        visitContents(visitor)
        visitor.afterVisitField(this)
    }

    override fun <D, R> accept(visitor: UastTypedVisitor<D, R>, data: D) = visitor.visitField(this, data)
}

interface ULocalVariable : UVariable, PsiLocalVariable {
    override val psi: PsiLocalVariable

    override fun asLogString() = log("name = $name")

    override fun accept(visitor: UastVisitor) {
        if (visitor.visitLocalVariable(this)) return
        visitContents(visitor)
        visitor.afterVisitLocalVariable(this)
    }

    override fun <D, R> accept(visitor: UastTypedVisitor<D, R>, data: D) = visitor.visitLocalVariable(this, data)
}

interface UEnumConstant : UField, UCallExpression, PsiEnumConstant {
    override val psi: PsiEnumConstant

    val initializingClass: UClass?

    override fun asLogString() = log("name = $name")

    override fun accept(visitor: UastVisitor) {
        if (visitor.visitEnumConstant(this)) return
        annotations.acceptList(visitor)
        methodIdentifier?.accept(visitor)
        classReference?.accept(visitor)
        valueArguments.acceptList(visitor)
        initializingClass?.accept(visitor)
        visitor.afterVisitEnumConstant(this)
    }

    override fun <D, R> accept(visitor: UastTypedVisitor<D, R>, data: D) =
            visitor.visitEnumConstantExpression(this, data)

    override fun asRenderString() = buildString {
        if (annotations.isNotEmpty()) {
            annotations.joinTo(this, separator = " ", postfix = " ", transform = UAnnotation::asRenderString)
        }
        append(name ?: "<ERROR>")
        if (valueArguments.isNotEmpty()) {
            valueArguments.joinTo(this, prefix = "(", postfix = ")", transform = UExpression::asRenderString)
        }
        initializingClass?.let {
            appendln(" {")
            it.uastDeclarations.forEach { declaration ->
                appendln(declaration.asRenderString().withMargin)
            }
            append("}")
        }
    }
}