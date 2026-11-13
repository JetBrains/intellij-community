// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.test.env.core

import org.jetbrains.annotations.ApiStatus

/** A validated SHA-256 digest used by Python test environment providers. */
@ApiStatus.Internal
@JvmInline
value class Sha256 private constructor(val value: String) {
  companion object {
    private val PATTERN = Regex("[0-9a-fA-F]{64}")

    fun parse(value: String): Sha256? = value
      .removePrefix("sha256:")
      .takeIf(PATTERN::matches)
      ?.lowercase()
      ?.let(::Sha256)
  }
}
