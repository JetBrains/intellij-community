// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.j2k.ast

import org.jetbrains.kotlin.K1Deprecation
import org.jetbrains.kotlin.j2k.CodeBuilder
import org.jetbrains.kotlin.j2k.append
import org.jetbrains.kotlin.j2k.buildList

@K1Deprecation
enum class AnnotationUseTarget(val id: String) {
    File("file"), Param("param"), Get("get"), Set("set"), Field("field")
}

@K1Deprecation
class Annotation(val name: Identifier,
                 val arguments: List<Pair<Identifier?, DeferredElement<Expression>>>,
                 val newLineAfter: Boolean,
                 val target: AnnotationUseTarget? = null) : Element() {

    override fun generateCode(builder: CodeBuilder) {
        builder.append("@")
        target?.let {
            builder.append("${it.id}:")
        }
        if (arguments.isEmpty()) {
            builder.append(name)
        }
        else {
            generateCall(builder)
        }
    }

    fun generateCall(builder: CodeBuilder) {
        builder.append(name)
                .append("(")
                .buildList(
                        generators = arguments.map {
                            {
                                if (it.first != null) {
                                    builder append it.first!! append " = " append it.second
                                }
                                else {
                                    builder append it.second
                                }
                            }
                        },
                        separator = ", ")
                .append(")")
    }

    override fun postGenerateCode(builder: CodeBuilder) {
        // we add line break in postGenerateCode to keep comments attached to this element on the same line
        builder.append(if (newLineAfter) "\n" else " ")
    }
}

@K1Deprecation
class AnnotationConstructorCall(name: Identifier, arguments: List<Pair<Identifier?, DeferredElement<Expression>>>) : Expression() {
    private val annotation = Annotation(name, arguments, false)

    override fun generateCode(builder: CodeBuilder) {
        annotation.generateCall(builder)
    }
}

@K1Deprecation
class Annotations(val annotations: List<Annotation>) : Element() {
    override fun generateCode(builder: CodeBuilder) {
        builder.append(annotations, "")
    }

    override val isEmpty: Boolean
        get() = annotations.isEmpty()

    operator fun plus(other: Annotations) = Annotations(annotations + other.annotations).assignNoPrototype()

    companion object {
        val Empty = Annotations(listOf())
    }
}
