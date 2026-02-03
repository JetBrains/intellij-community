// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.j2k.ast

import org.jetbrains.kotlin.K1Deprecation
import org.jetbrains.kotlin.j2k.CodeBuilder

@K1Deprecation
class Enum(
        name: Identifier,
        annotations: Annotations,
        modifiers: Modifiers,
        typeParameterList: TypeParameterList,
        implementsTypes: List<Type>,
        body: ClassBody
) : Class(name, annotations, modifiers, typeParameterList, emptyList(), null, implementsTypes, body) {

    override fun generateCode(builder: CodeBuilder) {
        builder append annotations appendWithSpaceAfter presentationModifiers() append "enum class " append name

        if (body.primaryConstructorSignature != null) {
            builder.append(body.primaryConstructorSignature)
        }

        builder append typeParameterList

        appendBaseTypes(builder)

        body.appendTo(builder)
    }
}
