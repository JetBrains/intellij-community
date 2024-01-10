// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.j2k.ast

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.j2k.CodeBuilder
import org.jetbrains.kotlin.j2k.append

class MethodCallExpression(
        val methodExpression: Expression,
        val argumentList: ArgumentList,
        val typeArguments: List<Type>,
        override val isNullable: Boolean
) : Expression() {

    override fun generateCode(builder: CodeBuilder) {
        builder.appendOperand(this, methodExpression).append(typeArguments, ", ", "<", ">")
        builder.append(argumentList)
    }

    companion object {
        fun buildNonNull(
                receiver: Expression?,
                methodName: String,
                argumentList: ArgumentList = ArgumentList.withNoPrototype(),
                typeArguments: List<Type> = emptyList(),
                dotPrototype: PsiElement? = null
        ): MethodCallExpression = build(receiver, methodName, argumentList, typeArguments, false, dotPrototype)

        fun build(
                receiver: Expression?,
                methodName: String,
                argumentList: ArgumentList,
                typeArguments: List<Type>,
                isNullable: Boolean,
                dotPrototype: PsiElement? = null
        ): MethodCallExpression {
            val identifier = Identifier.withNoPrototype(methodName, isNullable = false)
            val methodExpression = if (receiver != null)
                QualifiedExpression(receiver, identifier, dotPrototype).assignNoPrototype()
            else
                identifier
            return MethodCallExpression(methodExpression, argumentList, typeArguments, isNullable)
        }
    }
}
