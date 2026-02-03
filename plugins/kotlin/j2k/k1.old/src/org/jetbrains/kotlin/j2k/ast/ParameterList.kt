// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.j2k.ast

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.K1Deprecation
import org.jetbrains.kotlin.j2k.CodeBuilder
import org.jetbrains.kotlin.j2k.append

@K1Deprecation
class ParameterList(
        val parameters: List<Parameter>,
        val lPar: LPar?,
        val rPar: RPar?
) : Element() {
    override fun generateCode(builder: CodeBuilder) {
        lPar?.let { builder.append(it) }

        builder.append(parameters, ", ")

        rPar?.let { builder.append(it) }
    }

    companion object {
        fun withNoPrototype(parameters: List<Parameter>): ParameterList {
            return ParameterList(parameters, LPar.withPrototype(null), RPar.withPrototype(null)).assignNoPrototype()
        }
    }
}

// we use LPar and RPar elements to better handle comments and line breaks around them
@K1Deprecation
class LPar private constructor() : Element() {
    override fun generateCode(builder: CodeBuilder) {
        builder.append("(")
    }

    companion object {
        fun withPrototype(element: PsiElement?) = LPar().assignPrototype(element, CommentsAndSpacesInheritance.LINE_BREAKS)
    }
}

@K1Deprecation
class RPar private constructor() : Element() {
    override fun generateCode(builder: CodeBuilder) {
        builder.append(")")
    }

    companion object {
        fun withPrototype(element: PsiElement?) = RPar().assignPrototype(element, CommentsAndSpacesInheritance.LINE_BREAKS)
    }
}
