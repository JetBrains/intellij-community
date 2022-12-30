// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.j2k.ast

import com.intellij.psi.PsiTypeParameter
import com.intellij.psi.PsiTypeParameterList
import org.jetbrains.kotlin.j2k.CodeBuilder
import org.jetbrains.kotlin.j2k.Converter
import org.jetbrains.kotlin.j2k.append
import org.jetbrains.kotlin.j2k.buildList

class TypeParameter(val name: Identifier, private val extendsTypes: List<Type>) : Element() {
    fun hasWhere(): Boolean = extendsTypes.size > 1

    fun whereToKotlin(builder: CodeBuilder) {
        if (hasWhere()) {
            builder append name append " : " append extendsTypes[1]
        }
    }

    override fun generateCode(builder: CodeBuilder) {
        builder append name
        if (extendsTypes.isNotEmpty()) {
            builder append " : " append extendsTypes[0]
        }
    }
}

class TypeParameterList(val parameters: List<TypeParameter>) : Element() {
    override fun generateCode(builder: CodeBuilder) {
        if (parameters.isNotEmpty()) builder.append(parameters, ", ", "<", ">")
    }

    fun appendWhere(builder: CodeBuilder): CodeBuilder {
        if (hasWhere()) {
            builder.buildList(generators = parameters.map { { it.whereToKotlin(builder) } },
                              separator = ", ",
                              prefix = " where ",
                              suffix = "")
        }
        return builder
    }

    override val isEmpty: Boolean
        get() = parameters.isEmpty()

    private fun hasWhere(): Boolean = parameters.any { it.hasWhere() }

    companion object {
        val Empty = TypeParameterList(listOf())
    }
}

private fun Converter.convertTypeParameter(typeParameter: PsiTypeParameter): TypeParameter {
    return TypeParameter(typeParameter.declarationIdentifier(),
                           typeParameter.extendsListTypes.map { typeConverter.convertType(it) }).assignPrototype(typeParameter)
}

fun Converter.convertTypeParameterList(typeParameterList: PsiTypeParameterList?): TypeParameterList {
    return if (typeParameterList != null)
        TypeParameterList(typeParameterList.typeParameters.toList().map { convertTypeParameter(it) }).assignPrototype(typeParameterList)
    else
        TypeParameterList.Empty
}
