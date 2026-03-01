// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.j2k.ast

import org.jetbrains.kotlin.K1Deprecation

@K1Deprecation
class Interface(
        name: Identifier,
        annotations: Annotations,
        modifiers: Modifiers,
        typeParameterList: TypeParameterList,
        extendsTypes: List<Type>,
        implementsTypes: List<Type>,
        body: ClassBody
) : Class(name, annotations, modifiers, typeParameterList, extendsTypes, null, implementsTypes, body) {

    override val keyword: String
        get() = "interface"

    override fun presentationModifiers(): Modifiers
            = modifiers.filter { it in ACCESS_MODIFIERS }
}
