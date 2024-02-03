// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.j2k.ast

import org.jetbrains.kotlin.j2k.CodeBuilder
import org.jetbrains.kotlin.j2k.append

class PackageStatement(val packageName: String) : Element() {
    override fun generateCode(builder: CodeBuilder) {
        builder append "package " append packageName
    }
}

class File(val elements: List<Element>) : Element() {
    override fun generateCode(builder: CodeBuilder) {
        builder.append(elements, "\n")
    }
}
