// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.backend.hyperlinks.filter

internal fun CharSequence.isCharAt(index: Int, char: Char): Boolean {
  return index in indices && this[index] == char
}
