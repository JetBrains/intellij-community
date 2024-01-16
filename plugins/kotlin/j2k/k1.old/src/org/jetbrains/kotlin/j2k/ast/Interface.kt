// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.j2k.ast

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
