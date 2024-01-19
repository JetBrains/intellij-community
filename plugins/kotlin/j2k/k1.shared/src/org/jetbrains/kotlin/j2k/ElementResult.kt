// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.j2k

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.name.FqName

@ApiStatus.Internal
data class ElementResult(
    val text: String,
    val importsToAdd: Set<FqName>,
    val parseContext: ParseContext
)
