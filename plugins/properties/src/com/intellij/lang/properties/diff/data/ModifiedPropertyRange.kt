// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.properties.diff.data

/**
 * Represents a [SemiOpenLineRange] of changed property in both versions of the file.
 */
internal data class ModifiedPropertyRange(val left: SemiOpenLineRange, val right: SemiOpenLineRange) {
  override fun toString(): String {
    return "ModifiedPropertyRange: left: [${left.startLine}, ${left.endLine}), right: [${right.startLine}, ${right.endLine})"
  }
}