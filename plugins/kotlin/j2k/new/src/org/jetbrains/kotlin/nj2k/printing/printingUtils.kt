// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.nj2k.printing

import org.jetbrains.kotlin.nj2k.escaped

internal fun String.escapedAsQualifiedName(): String =
    split('.')
        .map { it.escaped() }
        .joinToString(".") { it }