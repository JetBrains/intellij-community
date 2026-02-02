// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.j2k.ast

import org.jetbrains.kotlin.K1Deprecation
import org.jetbrains.kotlin.j2k.CodeBuilder

@K1Deprecation
abstract class Expression : Statement() {
    open val isNullable: Boolean get() = false

    object Empty : Expression() {
        override fun generateCode(builder: CodeBuilder) {}
        override val isEmpty: Boolean get() = true
    }
}
