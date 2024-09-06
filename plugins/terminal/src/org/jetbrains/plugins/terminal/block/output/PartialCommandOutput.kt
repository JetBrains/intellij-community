// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.output

import org.jetbrains.plugins.terminal.block.session.StyleRange

/**
 * @param styles ranges inside the [text] bounds.
 * @param logicalLineIndex absolut index of the logical line where provided [text] starts in the command output.
 * @param terminalWidth width at the moment of partial output collection.
 */
internal data class PartialCommandOutput(
  val text: String,
  val styles: List<StyleRange>,
  val logicalLineIndex: Int,
  val terminalWidth: Int
)