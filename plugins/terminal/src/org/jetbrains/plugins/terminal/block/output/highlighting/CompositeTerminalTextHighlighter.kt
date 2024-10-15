// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.output.highlighting

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.util.ClearableLazyValue
import org.jetbrains.plugins.terminal.block.output.CommandBlock
import org.jetbrains.plugins.terminal.block.output.TerminalOutputModel
import org.jetbrains.plugins.terminal.block.output.TerminalOutputModelListener
import org.jetbrains.plugins.terminal.block.output.TerminalTextHighlighter
import org.jetbrains.plugins.terminal.block.output.highlighting.TerminalCommandBlockHighlighterProvider.Companion.COMMAND_BLOCK_HIGHLIGHTER_PROVIDER_EP_NAME
import org.jetbrains.plugins.terminal.block.ui.invokeLater

/**
 * A composite implementation of EditorHighlighter, which allows for highlighting different command blocks using different highlighters.
 *
 * terminalTextHighlighter: the terminal text highlighter used when:
 * 1) there are no applicable block highlighters
 * 2) at least two highlighters are applicable
 */
internal class CompositeTerminalTextHighlighter(
  private val terminalOutputModel: TerminalOutputModel,
  terminalTextHighlighter: TerminalTextHighlighter,
  parentDisposable: Disposable,
) : CompositeEditorHighlighter(terminalTextHighlighter) {

  private val terminalCommandBlockHighlighters: ClearableLazyValue<List<TerminalCommandBlockHighlighter>> = ClearableLazyValue.create {
    COMMAND_BLOCK_HIGHLIGHTER_PROVIDER_EP_NAME
      .extensionList
      .map { it.getHighlighter(terminalOutputModel.editor.colorsScheme) }
  }

  init {
    terminalOutputModel.addListener(object : TerminalOutputModelListener {
      override fun blockCreated(block: CommandBlock) {
        setCommandBlock(block)
      }
    })
    COMMAND_BLOCK_HIGHLIGHTER_PROVIDER_EP_NAME.addExtensionPointListener(object : ExtensionPointListener<TerminalCommandBlockHighlighterProvider> {
      override fun extensionRemoved(extension: TerminalCommandBlockHighlighterProvider, pluginDescriptor: PluginDescriptor) {
        invokeLater {
          terminalCommandBlockHighlighters.drop()
        }
      }
    }, parentDisposable)
  }

  override val switchableEditorHighlighters: List<SwitchableEditorHighlighter>
    get() = terminalCommandBlockHighlighters.value

  override fun documentChanged(event: DocumentEvent) {
    terminalCommandBlockHighlighters.value.forEach { it.documentChanged(event) }
  }

  fun setCommandBlock(block: CommandBlock) {
    terminalCommandBlockHighlighters.value.forEach { highlighter ->
      highlighter.applyHighlightingInfoToBlock(block)
    }
  }
}
