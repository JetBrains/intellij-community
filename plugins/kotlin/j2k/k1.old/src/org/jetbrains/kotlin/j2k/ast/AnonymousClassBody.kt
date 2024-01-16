// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.j2k.ast

import org.jetbrains.kotlin.j2k.CodeBuilder

class AnonymousClassBody(body: ClassBody, val extendsInterface: Boolean)
: Class(Identifier.Empty, Annotations.Empty, Modifiers.Empty, TypeParameterList.Empty, listOf(), null, listOf(), body) {
    override fun generateCode(builder: CodeBuilder) {
        body.appendTo(builder)
    }
}
