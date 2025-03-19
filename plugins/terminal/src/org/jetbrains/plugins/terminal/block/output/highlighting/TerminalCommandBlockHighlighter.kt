// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.output.highlighting

import com.intellij.openapi.editor.highlighter.EditorHighlighter
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.block.output.CommandBlock

@ApiStatus.Internal
interface TerminalCommandBlockHighlighter : EditorHighlighter, SwitchableEditorHighlighter {
  fun applyHighlightingInfoToBlock(block: CommandBlock)
}
