// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.dsl

import org.jetbrains.annotations.NonNls

/**
 * @author Vitaliy.Bibaev
 */
interface Convertable {
  @NonNls
  fun toCode(indent: Int = 0): String

  fun String.withIndent(indent: Int): String = "  ".repeat(indent) + this
}