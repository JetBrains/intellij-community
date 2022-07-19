// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.starter.models

data class VMOptionsDiff(val originalLines: List<String>,
                         val actualLines: List<String>
) {
  val newLines = actualLines - originalLines
  val missingLines = originalLines - actualLines
  val isEmpty = newLines.isEmpty() && missingLines.isEmpty()

  override fun toString(): String {
    return "VMOptionsDiff(newLines=$newLines, missingLines=$missingLines)"
  }
}