// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.frontend.view.hyperlinks

import com.intellij.execution.filters.Filter
import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.execution.filters.UrlFilter
import com.intellij.execution.impl.EditorTextDecoration
import com.intellij.execution.impl.EditorTextDecorationApplier
import com.intellij.execution.impl.EditorTextDecorationId
import com.intellij.execution.impl.buildHyperlink
import com.intellij.execution.impl.createTextDecorationId
import com.intellij.ide.setToolTipText
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseMotionListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.terminal.frontend.view.impl.toRelative
import com.intellij.util.asDisposable
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.terminal.view.TerminalContentChangeEvent
import org.jetbrains.plugins.terminal.view.TerminalOutputModel
import org.jetbrains.plugins.terminal.view.TerminalOutputModelListener
import org.jetbrains.plugins.terminal.view.TerminalOutputOsc8Hyperlink
import java.util.concurrent.atomic.AtomicLong

/**
 * Installs rendering of OSC8 hyperlinks ([TerminalOutputOsc8Hyperlink]) captured by the emulator
 * and delivered through the output model.
 *
 * Unlike the filter-based hyperlinks (see `FrontendTerminalHyperlinksProcessing`), an OSC8 link's
 * text range and target URI are already known, so no text scanning and no backend round trip are needed.
 * The target URI is checked by the platform [UrlFilter]: recognized URIs become clickable links,
 * and clicking navigates via the produced [HyperlinkInfo].
 *
 * Hovering a link shows the target URI in a tooltip: the link text is arbitrary, so the tooltip is
 * the only way to see the real destination before clicking.
 *
 * Because the OSC8 decorations share the editor's applier with the filter-based hyperlinks,
 * [FrontendTerminalHyperlinkFacade.getHoveredHyperlinkId] may report the id of an OSC8 decoration.
 * Such an id is unknown to the backend hyperlinks session, so
 * `org.jetbrains.plugins.terminal.hyperlinks.menu.HyperlinkContextMenuActionGroup` contributes nothing for it:
 * OSC8 links have no hyperlink context menu. That is a known limitation, not a failure mode.
 */
@ApiStatus.Internal
fun installOsc8HyperlinksProcessing(
  project: Project,
  outputModel: TerminalOutputModel,
  editor: EditorEx,
  // Shared with the filter-based hyperlinks of the same editor (see [installHyperlinksProcessing]).
  applier: EditorTextDecorationApplier,
  coroutineScope: CoroutineScope,
): FrontendOsc8HyperlinksFacade {
  // Counts the output model changes seen and the changes already reflected in the editor decorations.
  // The output model's own modification stamp can't be used here: an update that only drops an OSC8
  // escape leaves the text (and therefore the stamp) untouched, but still needs a reconciliation.
  // [observedChanges] starts ahead of [reconciledChanges] so that the initial content, if any, gets reconciled too.
  val observedChanges = MutableStateFlow(1L)
  val reconciledChanges = MutableStateFlow(0L)
  val renderer = Osc8HyperlinksRenderer(project, outputModel, applier)

  outputModel.addListener(coroutineScope.asDisposable(), object : TerminalOutputModelListener {
    override fun afterContentChanged(event: TerminalContentChangeEvent) {
      observedChanges.update { it + 1 }
    }
  })

  // The hovered decoration is tracked by the applier's own mouse listener. It runs before this one
  // on every mouse move because the applier is always created (and its listener registered) earlier.
  editor.addEditorMouseMotionListener(object : EditorMouseMotionListener {
    private var ownToolTip: String? = null

    override fun mouseMoved(e: EditorMouseEvent) {
      val toolTip: HtmlChunk? = renderer.hoveredLinkUri()?.let {
        HtmlChunk.text(it).wrapWith(HtmlChunk.html())
      }
      val component = editor.contentComponent
      // Clear only the tooltip this listener set; a tooltip set by some other component is kept.
      if (toolTip != null || (ownToolTip != null && component.toolTipText == ownToolTip)) {
        component.setToolTipText(toolTip)
      }
      ownToolTip = toolTip?.toString()
    }
  }, coroutineScope.asDisposable())

  // The reconciliation reads the model and mutates the editor markup, so it must run on the EDT
  // and outside the output model's own content-change notification; collecting the StateFlow
  // also coalesces bursts of changes.
  // Can't use Dispatchers.UI because the editor markup can require locks.
  coroutineScope.launch(Dispatchers.EDT + ModalityState.any().asContextElement() + CoroutineName("Terminal OSC8 hyperlinks")) {
    observedChanges.collect { observed ->
      // `observed` was captured before the pass: changes arriving during it are not reported as reconciled.
      renderer.reconcile()
      reconciledChanges.value = observed
    }
  }

  return FrontendOsc8HyperlinksFacade(observedChanges, reconciledChanges)
}

@ApiStatus.Internal
class FrontendOsc8HyperlinksFacade internal constructor(
  private val observedChanges: StateFlow<Long>,
  private val reconciledChanges: Flow<Long>,
) {
  /**
   * Suspends until every output model change made so far is reflected in the editor decorations.
   */
  @TestOnly
  suspend fun awaitReconciled() {
    val target = observedChanges.value
    reconciledChanges.first { it >= target }
  }
}

private class Osc8HyperlinksRenderer(
  private val project: Project,
  private val outputModel: TerminalOutputModel,
  private val applier: EditorTextDecorationApplier,
) {
  private val urlFilter: Filter = UrlFilter(project)

  // Negative ids keep OSC8 decorations disjoint from the filter-based hyperlinks that share the applier
  // (those are assigned positive ids by the backend).
  private val idCounter = AtomicLong(-1)

  /** Decorations applied by the previous [reconcile] pass: decoration id -> the link's target URI. */
  private val appliedUriById = LinkedHashMap<EditorTextDecorationId, String>()

  /**
   * Brings the applied decorations in sync with the links currently present in the output model.
   *
   * Rescans the whole link list and recreates every decoration rather than diffing against the previous
   * pass: OSC8 links are rare, so the list is almost always empty or tiny, and recreating keeps the
   * bookkeeping trivially correct under trimming. Recreating is also what makes the pass self-healing:
   * a document replace can invalidate a decoration's range highlighter without changing the link itself
   * (same offsets, same target), so a diff keyed by the link would never restore its decoration.
   */
  @RequiresEdt
  fun reconcile() {
    val links = outputModel.getOsc8Hyperlinks()
    val toAdd = ArrayList<EditorTextDecoration>()
    val addedUriById = LinkedHashMap<EditorTextDecorationId, String>()
    for (link in links) {
      val hyperlinkInfo = resolve(link.uri) ?: continue
      val decoration = createDecoration(link, hyperlinkInfo)
      toAdd.add(decoration)
      addedUriById[decoration.id] = link.uri
    }

    applier.removeDecorations(appliedUriById.keys)
    appliedUriById.clear()
    appliedUriById.putAll(addedUriById)
    applier.addDecorations(toAdd)
  }

  /**
   * The target URI of the hovered OSC8 link, or `null` if no link is hovered.
   *
   * Relies on the hover tracking of [applier]; a hovered filter-based hyperlink is not in
   * [appliedUriById], so it yields `null`.
   */
  @RequiresEdt
  fun hoveredLinkUri(): @NlsSafe String? {
    val hovered = applier.getHoveredHyperlink() ?: return null
    return appliedUriById[hovered.id]
  }

  private fun createDecoration(link: TerminalOutputOsc8Hyperlink, hyperlinkInfo: HyperlinkInfo): EditorTextDecoration {
    return buildHyperlink(
      id = createTextDecorationId(idCounter.getAndDecrement()),
      startOffset = link.startOffset.toRelative(outputModel),
      endOffset = link.endOffset.toRelative(outputModel),
      action = { hyperlinkInfo.navigate(project) },
    ) {
      isInvisibleLink = true
    }
  }

  /**
   * A target is a link only if [UrlFilter] matches it over its whole length.
   */
  private fun resolve(uri: String): HyperlinkInfo? {
    val result = urlFilter.applyFilter(uri, uri.length) ?: return null
    return result.resultItems.firstOrNull {
      it.highlightStartOffset == 0 && it.highlightEndOffset == uri.length && it.hyperlinkInfo != null
    }?.hyperlinkInfo
  }
}
