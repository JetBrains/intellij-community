// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.j2k.ast

import org.jetbrains.kotlin.K1Deprecation
import org.jetbrains.kotlin.j2k.CodeBuilder

@K1Deprecation
class LocalVariable(
    private val identifier: Identifier,
    private val annotations: Annotations,
    private val explicitType: Type?,
    private val initializer: Expression,
    private val isVal: Boolean
) : Element() {

    override fun generateCode(builder: CodeBuilder) {
        if(initializer is AssignmentExpression)
            builder append initializer append "\n"
        builder append annotations append (if (isVal) "val " else "var ") append identifier
        if (explicitType != null) {
            builder append ":" append explicitType
        }
        if (!initializer.isEmpty) {
            builder append " = "
            if(initializer is AssignmentExpression)
                builder append initializer.left
            else
                builder append initializer
        }
    }
}
