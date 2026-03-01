// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.j2k.ast

import org.jetbrains.kotlin.K1Deprecation
import org.jetbrains.kotlin.j2k.CodeBuilder
import org.jetbrains.kotlin.j2k.append

@K1Deprecation
class ReferenceElement(val name: Identifier, val typeArgs: List<Element>) : Element() {
    override fun generateCode(builder: CodeBuilder) {
        builder.append(name).append(typeArgs, ", ", "<", ">")
    }
}
