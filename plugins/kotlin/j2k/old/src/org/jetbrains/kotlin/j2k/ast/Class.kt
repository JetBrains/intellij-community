// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.j2k.ast

import org.jetbrains.kotlin.j2k.CodeBuilder
import org.jetbrains.kotlin.j2k.append
import org.jetbrains.kotlin.j2k.buildList

open class Class(
  val name: Identifier,
  annotations: Annotations,
  modifiers: Modifiers,
  val typeParameterList: TypeParameterList,
  private val extendsTypes: List<Type>,
  private val baseClassParams: List<DeferredElement<Expression>>?,
  private val implementsTypes: List<Type>,
  val body: ClassBody
) : Member(annotations, modifiers) {

    override fun generateCode(builder: CodeBuilder) {
        builder.append(annotations)
                .appendWithSpaceAfter(presentationModifiers())
                .append(keyword)
                .append(" ")
                .append(name)
                .append(typeParameterList)

        if (body.primaryConstructorSignature != null) {
            builder.append(body.primaryConstructorSignature)
        }

        appendBaseTypes(builder)
        typeParameterList.appendWhere(builder)

        body.appendTo(builder)
    }

    protected open val keyword: String
        get() = "class"

    protected fun appendBaseTypes(builder: CodeBuilder) {
        builder.buildList(generators = baseClassSignatureWithParams(builder) + implementsTypes.map { { builder.append(it) } },
                          separator = ", ",
                          prefix = ":")
    }

    private fun baseClassSignatureWithParams(builder: CodeBuilder): List<() -> CodeBuilder> {
        if (keyword == "class" && extendsTypes.size == 1 && baseClassParams != null) {
            return listOf {
                builder append extendsTypes[0] append "("
                builder.append(baseClassParams, ", ")
                builder append ")"
            }
        }
        return extendsTypes.map { { builder.append(it) } }
    }

    protected open fun presentationModifiers(): Modifiers
            = if (modifiers.contains(Modifier.ABSTRACT)) modifiers.without(Modifier.OPEN) else modifiers
}

class Object(
        name: Identifier,
        annotations: Annotations,
        modifiers: Modifiers,
        body: ClassBody
) : Class(name, annotations, modifiers, TypeParameterList.Empty, emptyList(), null, emptyList(), body) {

    override val keyword: String
        get() = "object"
}
