// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.evaluate.compilation

import org.jetbrains.kotlin.idea.debugger.evaluate.compilation.CodeFragmentParameter.Dumb

interface CodeFragmentParameter {
    val kind: Kind
    val name: String
    val debugString: String
    val depthRelativeToCurrentFrame: Int

    enum class Kind {
        ORDINARY, DELEGATED, EXTENSION_RECEIVER, DISPATCH_RECEIVER, CONTEXT_RECEIVER, COROUTINE_CONTEXT, LOCAL_FUNCTION,
        FAKE_JAVA_OUTER_CLASS, FIELD_VAR, FOREIGN_VALUE
    }

    data class Dumb(
        override val kind: Kind,
        override val name: String,
        override val depthRelativeToCurrentFrame: Int,
        override val debugString: String = name,
    ) : CodeFragmentParameter
}

interface CodeFragmentParameterInfo {
    val parameters: List<Dumb>
    val crossingBounds: Set<Dumb>
}

class K2CodeFragmentParameterInfo(
    override val parameters: List<Dumb>,
    override val crossingBounds: Set<Dumb>
) : CodeFragmentParameterInfo