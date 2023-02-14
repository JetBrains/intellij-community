// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.j2k.ast

import org.jetbrains.kotlin.j2k.CodeBuilder

abstract class FunctionLike(
        annotations: Annotations,
        modifiers: Modifiers,
        open val parameterList: ParameterList?,
        val body: DeferredElement<Block>?
) : Member(annotations, modifiers) {

    protected open fun presentationModifiers(): Modifiers = modifiers
}

class Function(
        val name: Identifier,
        annotations: Annotations,
        modifiers: Modifiers,
        val returnType: Type,
        val typeParameterList: TypeParameterList,
        parameterList: ParameterList,
        body: DeferredElement<Block>?,
        private val isInInterface: Boolean
) : FunctionLike(annotations, modifiers, parameterList, body) {

    override val parameterList: ParameterList
        get() = super.parameterList!!

    override fun presentationModifiers(): Modifiers {
        var modifiers = this.modifiers
        if (isInInterface) {
            modifiers = modifiers.without(Modifier.ABSTRACT)
        }

        if (modifiers.contains(Modifier.OVERRIDE)) {
            modifiers = modifiers.filter { it != Modifier.OPEN }
        }

        return modifiers
    }

    override fun generateCode(builder: CodeBuilder) {
        builder.append(annotations)
                .appendWithSpaceAfter(presentationModifiers())
                .append("fun ")
                .appendWithSuffix(typeParameterList, " ")
                .append(name)
                .append(parameterList)

        if (!returnType.isUnit()) {
            builder append ":" append returnType
        }

        typeParameterList.appendWhere(builder)

        if (body != null) {
            builder append " " append body
        }
    }
}

class AnonymousFunction(
        returnType: Type,
        typeParameterList: TypeParameterList,
        parameterList: ParameterList,
        body: DeferredElement<Block>?
): Expression() {
    private val function = Function(Identifier.Empty, Annotations.Empty, Modifiers.Empty, returnType, typeParameterList, parameterList, body, false)

    override fun generateCode(builder: CodeBuilder) = function.generateCode(builder)
}
