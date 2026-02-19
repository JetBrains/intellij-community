// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.j2k.ast

import org.jetbrains.kotlin.K1Deprecation
import org.jetbrains.kotlin.j2k.CodeBuilder
import org.jetbrains.kotlin.j2k.append

@K1Deprecation
class Import(val name: String) : Element() {
    override fun generateCode(builder: CodeBuilder) {
        builder append "import " append name
    }
}

@K1Deprecation
class ImportList(var imports: List<Import>) : Element() {
    override val isEmpty: Boolean
        get() = imports.isEmpty()

    override fun generateCode(builder: CodeBuilder) {
        builder.append(imports, "\n")
    }
}
