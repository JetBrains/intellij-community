// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.j2k.ast

import org.jetbrains.kotlin.K1Deprecation
import org.jetbrains.kotlin.j2k.CodeBuilder

@K1Deprecation
class AnonymousClassBody(body: ClassBody, val extendsInterface: Boolean)
: Class(Identifier.Empty, Annotations.Empty, Modifiers.Empty, TypeParameterList.Empty, listOf(), null, listOf(), body) {
    override fun generateCode(builder: CodeBuilder) {
        body.appendTo(builder)
    }
}
