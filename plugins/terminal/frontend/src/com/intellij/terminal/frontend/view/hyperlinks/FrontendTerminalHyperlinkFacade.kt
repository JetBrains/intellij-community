package com.intellij.terminal.frontend.view.hyperlinks

import com.intellij.execution.impl.EditorTextDecorationApplier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.terminal.session.impl.TerminalHyperlinkId
import org.jetbrains.plugins.terminal.session.impl.toTerminalId

@ApiStatus.Internal
class FrontendTerminalHyperlinkFacade(
  private val applier: EditorTextDecorationApplier,
  private val lastFinishedTaskStamp: Flow<Long>,
) {
  fun getHoveredHyperlinkId(): TerminalHyperlinkId? {
    return applier.getHoveredHyperlink()?.id?.toTerminalId()
  }

  /**
   * Suspends until a highlighting task with `documentModificationStamp >= [targetStamp]` has finished.
   */
  @TestOnly
  suspend fun awaitProcessed(targetStamp: Long) {
    lastFinishedTaskStamp.first { it >= targetStamp }
  }
}
