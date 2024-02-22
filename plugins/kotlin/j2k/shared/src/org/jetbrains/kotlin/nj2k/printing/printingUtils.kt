// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k.printing

import org.jetbrains.kotlin.nj2k.escaped

fun String.escapedAsQualifiedName(): String =
    split('.')
        .map { it.escaped() }
        .joinToString(".") { it }