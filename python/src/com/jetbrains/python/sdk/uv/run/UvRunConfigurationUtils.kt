// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.uv.run

private val whiteSpaceRegex = Regex("\\s+")

internal fun String.splitParams(): List<String> = this.trim().split(whiteSpaceRegex).filter { it != "" }
