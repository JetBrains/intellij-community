// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.j2k.post.processing.inference.common

import org.jetbrains.kotlin.K1Deprecation

@K1Deprecation
enum class State {
    // lower state in the terms of subtyping relation,
    // e.g., for nullability this is not null type as T <: T?
    // for mutability this is MutableCollection as MutableCollection <: Collection
    LOWER,

    // the same as with lower but upper
    UPPER,

    // the type variable state is needed to be calculated
    UNKNOWN,

    // we don't need to infer state of that type variable
    UNUSED
}