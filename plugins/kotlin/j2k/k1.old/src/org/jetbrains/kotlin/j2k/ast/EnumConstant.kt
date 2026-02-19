// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.j2k.ast

import org.jetbrains.kotlin.K1Deprecation
import org.jetbrains.kotlin.j2k.CodeBuilder

@K1Deprecation
class EnumConstant(
        val identifier: Identifier,
        annotations: Annotations,
        modifiers: Modifiers,
        val params: DeferredElement<ArgumentList>?,
        val body: AnonymousClassBody?
) : Member(annotations, modifiers) {

    override fun generateCode(builder: CodeBuilder) {
        builder.append(annotations).append(identifier)

        if (params != null) {
            builder.append(params)
        }

        if (body != null) {
            builder.append(body)
        }
    }
}
