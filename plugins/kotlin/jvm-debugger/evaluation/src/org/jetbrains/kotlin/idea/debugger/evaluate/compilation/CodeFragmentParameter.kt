// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.evaluate.compilation

import org.jetbrains.kotlin.codegen.CodeFragmentCodegenInfo
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.types.KotlinType

interface CodeFragmentParameter {
    val kind: Kind
    val name: String
    val debugString: String

    enum class Kind {
        ORDINARY, DELEGATED, EXTENSION_RECEIVER, DISPATCH_RECEIVER, CONTEXT_RECEIVER, COROUTINE_CONTEXT, LOCAL_FUNCTION,
        FAKE_JAVA_OUTER_CLASS, FIELD_VAR, DEBUG_LABEL
    }

    class Smart(
        val dumb: Dumb,
        override val targetType: KotlinType,
        override val targetDescriptor: DeclarationDescriptor,
        override val isLValue: Boolean = false
    ) : CodeFragmentParameter by dumb, CodeFragmentCodegenInfo.IParameter

    data class Dumb(
        override val kind: Kind,
        override val name: String,
        override val debugString: String = name
    ) : CodeFragmentParameter
}