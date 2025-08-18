package com.intellij.terminal.frontend.hyperlinks

import com.intellij.execution.impl.*
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.terminal.frontend.TerminalInput
import com.intellij.terminal.session.*
import com.intellij.terminal.session.dto.TerminalHyperlinksModelStateDto
import com.intellij.terminal.session.dto.toFilterResultInfo
import com.intellij.util.asDisposable
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.plugins.terminal.block.reworked.TerminalOutputModel
import org.jetbrains.plugins.terminal.block.reworked.hyperlinks.TerminalHyperlinksModel

internal class FrontendTerminalHyperlinkFacade(
  private val isInAlternateBuffer: Boolean,
  editor: EditorEx,
  private val outputModel: TerminalOutputModel,
  private val terminalInput: TerminalInput,
  coroutineScope: CoroutineScope,
) {
  private val model = TerminalHyperlinksModel(if (isInAlternateBuffer) "Frontend AltBuf" else "Frontend Output", outputModel)
  private val applier = createDecorationApplier(editor, coroutineScope.asDisposable())

  fun updateHyperlinks(event: TerminalHyperlinksChangedEvent) {
    val removedFrom = event.removeFromOffset
    if (removedFrom != null) {
      removeHyperlinks(removedFrom)
    }
    addHyperlinks(event.hyperlinks.map { it.toFilterResultInfo() })
  }

  fun getHoveredHyperlinkId(): TerminalHyperlinkId? {
    return applier.getHoveredHyperlink()?.id?.toTerminalId()
  }

  fun restoreFromState(hyperlinksModelState: TerminalHyperlinksModelStateDto?) {
    if (hyperlinksModelState == null) return
    removeHyperlinks(0L)
    addHyperlinks(hyperlinksModelState.hyperlinks.map { it.toFilterResultInfo() })
  }

  private fun removeHyperlinks(absoluteStartOffset: Long) {
    val removed = model.removeHyperlinks(absoluteStartOffset)
    applier.removeDecorations(removed.map { it.toPlatformId() })
  }

  private fun addHyperlinks(hyperlinks: List<TerminalFilterResultInfo>) {
    // Unlike the backend variant, no timestamp checking here, because it was already checked at the backend.
    model.addHyperlinks(hyperlinks)
    applier.addDecorations(hyperlinks.mapNotNull { it.toEditorDecoration() })
  }

  private fun TerminalFilterResultInfo.toEditorDecoration(): EditorTextDecoration? =
    when (this) {
      is TerminalHyperlinkInfo -> {
        buildHyperlink(
          id = id.toPlatformId(),
          startOffset = outputModel.absoluteOffset(absoluteStartOffset).toRelative(),
          endOffset = outputModel.absoluteOffset(absoluteEndOffset).toRelative(),
          action = { terminalInput.sendLinkClicked(isInAlternateBuffer, id, it) },
        ) {
          attributes = style
          followedAttributes = followedStyle
          hoveredAttributes = hoveredStyle
          layer = layer
        }
      }
      is TerminalHighlightingInfo -> style?.let { style ->
        buildHighlighting(
          id = id.toPlatformId(),
          startOffset = outputModel.absoluteOffset(absoluteStartOffset).toRelative(),
          endOffset = outputModel.absoluteOffset(absoluteEndOffset).toRelative(),
          attributes = style,
        ) {
          layer = layer
        }
      }
      is TerminalInlayInfo -> inlayProvider?.let { inlayProvider ->
        buildInlay(
          id = id.toPlatformId(),
          offset = outputModel.absoluteOffset(absoluteEndOffset).toRelative(), // for inlays the end offset corresponds to the position
          inlayProvider = inlayProvider,
        )
      }
    }
}
