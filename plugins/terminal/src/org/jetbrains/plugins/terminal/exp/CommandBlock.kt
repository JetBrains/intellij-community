// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

internal sealed interface CommandBlock {
  val command: String?
  val prompt: String?
  val rightPrompt: String?

  val startOffset: Int
  val endOffset: Int
  val commandStartOffset: Int
  val outputStartOffset: Int

  /** If block is finalized it means that its length won't be expanded if some text is added before or after it */
  val isFinalized: Boolean
}