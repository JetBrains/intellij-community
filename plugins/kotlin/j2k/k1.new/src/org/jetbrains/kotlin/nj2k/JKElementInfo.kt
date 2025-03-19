// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k

import org.jetbrains.kotlin.descriptors.FunctionDescriptor

sealed class SuperFunctionInfo

data class ExternalSuperFunctionInfo(val descriptor: FunctionDescriptor) : SuperFunctionInfo()

data class InternalSuperFunctionInfo(val label: JKElementInfoLabel) : SuperFunctionInfo()

data class FunctionInfo(val superFunctions: List<SuperFunctionInfo>) : JKElementInfo

enum class JKTypeInfo(val unknownNullability: Boolean, val unknownMutability: Boolean) : JKElementInfo {
    KNOWN_NULLABILITY_KNOWN_MUTABILITY(unknownNullability = false, unknownMutability = false),
    UNKNOWN_NULLABILITY_KNOWN_MUTABILITY(unknownNullability = true, unknownMutability = false),
    KNOWN_NULLABILITY_UNKNOWN_MUTABILITY(unknownNullability = false, unknownMutability = true),
    UNKNOWN_NULLABILITY_UNKNOWN_MUTABILITY(unknownNullability = true, unknownMutability = true)
    ;

    companion object {
        operator fun invoke(unknownNullability: Boolean, unknownMutability: Boolean): JKTypeInfo = when {
            !unknownNullability && !unknownMutability -> KNOWN_NULLABILITY_KNOWN_MUTABILITY
            unknownNullability && !unknownMutability -> UNKNOWN_NULLABILITY_KNOWN_MUTABILITY
            !unknownNullability && unknownMutability -> KNOWN_NULLABILITY_UNKNOWN_MUTABILITY
            else -> UNKNOWN_NULLABILITY_UNKNOWN_MUTABILITY
        }
    }
}