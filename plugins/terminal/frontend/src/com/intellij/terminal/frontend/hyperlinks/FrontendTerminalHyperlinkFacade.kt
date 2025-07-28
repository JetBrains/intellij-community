package com.intellij.terminal.frontend.hyperlinks

import com.intellij.execution.impl.EditorHyperlinkApplier
import com.intellij.execution.impl.Hyperlink
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.terminal.frontend.TerminalInput
import com.intellij.terminal.session.TerminalFilterResultInfo
import com.intellij.terminal.session.TerminalHighlightingInfo
import com.intellij.terminal.session.TerminalHyperlinkInfo
import com.intellij.terminal.session.TerminalHyperlinksChangedEvent
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
  private val applier = EditorHyperlinkApplier(editor, coroutineScope.asDisposable())

  fun updateHyperlinks(event: TerminalHyperlinksChangedEvent) {
    val removedFrom = event.removeFromOffset
    if (removedFrom != null) {
      removeHyperlinks(removedFrom)
    }
    addHyperlinks(event.hyperlinks.map { it.toFilterResultInfo() })
  }

  fun restoreFromState(hyperlinksModelState: TerminalHyperlinksModelStateDto?) {
    if (hyperlinksModelState == null) return
    removeHyperlinks(0L)
    addHyperlinks(hyperlinksModelState.hyperlinks.map { it.toFilterResultInfo() })
  }

  private fun removeHyperlinks(absoluteStartOffset: Long) {
    val removed = model.removeHyperlinks(absoluteStartOffset)
    for (id in removed) {
      applier.removeHyperlink(id.toPlatformId())
    }
  }

  private fun addHyperlinks(hyperlinks: List<TerminalFilterResultInfo>) {
    // Unlike the backend variant, no timestamp checking here, because it was already checked at the backend.
    model.addHyperlinks(hyperlinks)
    for (hyperlink in hyperlinks) {
      applier.addHyperlink(hyperlink.toPlatformHyperlink())
    }
  }

  private fun TerminalFilterResultInfo.toPlatformHyperlink(): Hyperlink =
    when (this) {
      is TerminalHyperlinkInfo -> Hyperlink(
        id = id.toPlatformId(),
        start = outputModel.absoluteOffset(absoluteStartOffset).toRelative(),
        end = outputModel.absoluteOffset(absoluteEndOffset).toRelative(),
        attributes = style,
        followedAttributes = followedStyle,
        hoveredAttributes = hoveredStyle,
        layer = layer,
        action = { terminalInput.sendLinkClicked(isInAlternateBuffer, id) },
      )
      is TerminalHighlightingInfo -> Hyperlink(
        id = id.toPlatformId(),
        start = outputModel.absoluteOffset(absoluteStartOffset).toRelative(),
        end = outputModel.absoluteOffset(absoluteEndOffset).toRelative(),
        attributes = style,
        followedAttributes = null,
        hoveredAttributes = null,
        layer = layer,
        action = null,
      )
    }
}
