// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.utils

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class PrefixStringEncoder(private val excludedSet: Set<String>, private val escapePrefix: String) {
  private val encodedExcludedSet: Set<String> = excludedSet.map { "$escapePrefix$it" }.toSet()
  private val doublePrefix = "$escapePrefix$escapePrefix"

  fun encode(input: String): String =
    if (excludedSet.contains(input) || input.startsWith(escapePrefix)) "$escapePrefix$input" else input

  fun decode(input: String): String =
    if (encodedExcludedSet.contains(input) || input.startsWith(doublePrefix)) input.substring(escapePrefix.length) else input
}