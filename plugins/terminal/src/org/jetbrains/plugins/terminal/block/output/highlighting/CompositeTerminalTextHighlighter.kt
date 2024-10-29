// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.output.highlighting

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.terminal.block.output.*
import org.jetbrains.plugins.terminal.block.output.highlighting.TerminalCommandBlockHighlighterProvider.Companion.COMMAND_BLOCK_HIGHLIGHTER_PROVIDER_EP_NAME
import org.jetbrains.plugins.terminal.block.output.TerminalTextHighlighter

/**
 * A composite implementation of EditorHighlighter, which allows for highlighting different command blocks using different highlighters.
 *
 * terminalTextHighlighter: the terminal text highlighter used when:
 * 1) there are no applicable block highlighters
 * 2) at least two highlighters are applicable
 */
internal class CompositeTerminalTextHighlighter(
  terminalOutputModel: TerminalOutputModel,
  terminalTextHighlighter: TerminalTextHighlighter,
  project: Project,
  private val terminalCommandBlockHighlighters: MutableList<TerminalCommandBlockHighlighter>,
) : CompositeEditorHighlighter(terminalTextHighlighter, terminalCommandBlockHighlighters) {

  init {
    terminalOutputModel.addListener(object : TerminalOutputModelListener {
      override fun blockCreated(block: CommandBlock) {
        setCommandBlock(block)
      }
    })
    COMMAND_BLOCK_HIGHLIGHTER_PROVIDER_EP_NAME.addExtensionPointListener(object : ExtensionPointListener<TerminalCommandBlockHighlighterProvider> {
      override fun extensionRemoved(extension: TerminalCommandBlockHighlighterProvider, pluginDescriptor: PluginDescriptor) {
        synchronized(terminalCommandBlockHighlighters) {
          terminalCommandBlockHighlighters.removeIf { highlighter -> highlighter::class == extension.getHighlighter(terminalOutputModel.editor.colorsScheme)::class }
        }
      }
    }, project.service<TerminalProjectDisposable>())
  }

  override fun documentChanged(event: DocumentEvent) {
    terminalCommandBlockHighlighters.forEach { it.documentChanged(event) }
  }

  fun setCommandBlock(block: CommandBlock) {
    terminalCommandBlockHighlighters.forEach { highlighter ->
      highlighter.applyHighlightingInfoToBlock(block)
    }
  }

  // Disposed on Terminal plugin unloading and/or project disposing
  @Service(Service.Level.PROJECT)
  private class TerminalProjectDisposable : Disposable {
    override fun dispose() {}
  }
}
