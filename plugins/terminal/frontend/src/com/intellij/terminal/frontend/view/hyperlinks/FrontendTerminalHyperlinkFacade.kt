package com.intellij.terminal.frontend.view.hyperlinks

import com.intellij.execution.impl.EditorTextDecorationApplier
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.terminal.hyperlinks.TerminalHyperlinkId
import org.jetbrains.plugins.terminal.hyperlinks.session.TerminalHyperlinksSession
import org.jetbrains.plugins.terminal.hyperlinks.session.TerminalHyperlinksSessionId
import org.jetbrains.plugins.terminal.hyperlinks.toTerminalId
import org.jetbrains.plugins.terminal.util.getNow

@ApiStatus.Internal
class FrontendTerminalHyperlinkFacade(
  private val sessionDeferred: Deferred<TerminalHyperlinksSession>,
  private val applier: EditorTextDecorationApplier,
  private val lastFinishedTaskStamp: Flow<Long>,
) {
  /** Can be null if session is not initialized yet (for example, backend requests are not yet performed, or it is not available) */
  val sessionId: TerminalHyperlinksSessionId?
    get() = sessionDeferred.getNow()?.id

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
