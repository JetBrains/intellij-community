// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.frontend.view.hyperlinks

import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.execution.impl.EditorTextDecoration
import com.intellij.execution.impl.EditorTextDecorationApplier
import com.intellij.execution.impl.EditorTextDecorationId
import com.intellij.execution.impl.buildHyperlink
import com.intellij.execution.impl.createTextDecorationId
import com.intellij.ide.setToolTipText
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.UI
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseMotionListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.terminal.frontend.view.impl.toRelative
import com.intellij.terminal.osc8.CompositeOsc8UriResolverWrapper
import com.intellij.terminal.osc8.Osc8UriResolver
import com.intellij.util.asDisposable
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.view.TerminalContentChangeEvent
import org.jetbrains.plugins.terminal.view.TerminalOutputModel
import org.jetbrains.plugins.terminal.view.TerminalOutputModelListener
import org.jetbrains.plugins.terminal.view.TerminalOutputOsc8Hyperlink
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Installs rendering of OSC8 hyperlinks ([TerminalOutputOsc8Hyperlink]) captured by the emulator
 * and delivered through the output model.
 *
 * Unlike the filter-based hyperlinks (see `FrontendTerminalHyperlinksProcessing`), an OSC8 link's
 * text range and target URI are already known, so no text scanning and no backend round trip are needed.
 * The target URI is resolved by the [Osc8UriResolver]s.
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
@OptIn(FlowPreview::class)
fun installOsc8HyperlinksProcessing(
  project: Project,
  outputModel: TerminalOutputModel,
  editor: EditorEx,
  // Shared with the filter-based hyperlinks of the same editor (see [installHyperlinksProcessing]).
  applier: EditorTextDecorationApplier,
  coroutineScope: CoroutineScope,
) {
  // Counts the changes seen that need a reconciliation; a change to this value triggers one.
  // The `outputModel.modificationStamp` can't be used here: an update that only
  // drops/creates/modifies an OSC8 link URI leaves the modificationStamp untouched.
  // The initial value is arbitrary: only changes to it matter.
  val observedChanges = MutableStateFlow(1L)
  val uriResolverWrapper = CompositeOsc8UriResolverWrapper(project, coroutineScope)
  val renderer = Osc8HyperlinksRenderer(project, outputModel, applier, uriResolverWrapper)

  outputModel.addListener(coroutineScope.asDisposable(), object : TerminalOutputModelListener {
    override fun afterContentChanged(event: TerminalContentChangeEvent) {
      observedChanges.update { it + 1 }
    }
  })

  uriResolverWrapper.addUpdateListener {
    observedChanges.update { it + 1 }
  }

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

  // The reconciliation reads the model and mutates the editor markup, so it must run
  // on the EDT thread and outside the output model's own content-change notification.
  // Sampling the StateFlow also limits reconciliation to once every DELAY,
  // no matter how many changes arrive in between.
  coroutineScope.launch(Dispatchers.UI + ModalityState.any().asContextElement() + CoroutineName("Terminal OSC8 hyperlinks")) {
    observedChanges.sample(DELAY).collect {
      renderer.reconcile()
    }
  }
}

private class Osc8HyperlinksRenderer(
  private val project: Project,
  private val outputModel: TerminalOutputModel,
  private val applier: EditorTextDecorationApplier,
  private val uriResolverWrapper: CompositeOsc8UriResolverWrapper,
) {
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
   *
   * The target URIs are resolved on a background thread, as [Osc8UriResolver] requires.
   * The document can change meanwhile, and a link from before the change could get
   * a decoration with offsets that no longer fit the document. So the links are read
   * again after the resolution if the document changed. A link added in between is
   * left to the pass that the change already scheduled.
   */
  suspend fun reconcile() {
    val stampBefore = outputModel.modificationStamp
    val linksBefore = currentLinks()
    val hyperlinkInfoByUri = resolve(linksBefore)
    // during the resolve, the document might have changed, so the links might be invalid
    val linksAfter = if (outputModel.modificationStamp == stampBefore) linksBefore else currentLinks()
    val (decorations, uriByDecorationId) = decorate(linksAfter, hyperlinkInfoByUri)

    applier.removeDecorations(appliedUriById.keys)
    appliedUriById.clear()
    appliedUriById.putAll(uriByDecorationId)
    applier.addDecorations(decorations)
  }

  private fun decorate(
    links: List<TerminalOutputOsc8Hyperlink>,
    hyperlinkInfoByUri: Map<String, HyperlinkInfo>,
  ): Pair<List<EditorTextDecoration>, Map<EditorTextDecorationId, String>> {
    val decorations = ArrayList<EditorTextDecoration>()
    val uriById = LinkedHashMap<EditorTextDecorationId, String>()
    for (link in links) {
      val hyperlinkInfo = hyperlinkInfoByUri[link.uri] ?: continue
      val decoration = createDecoration(link, hyperlinkInfo)
      decorations.add(decoration)
      uriById[decoration.id] = link.uri
    }
    return Pair(decorations, uriById)
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

  private fun currentLinks(): List<TerminalOutputOsc8Hyperlink> {
    return Osc8HyperlinkUtil.mergeSplitUrls(outputModel, outputModel.getOsc8Hyperlinks())
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

  private suspend fun resolve(hyperlinks: List<TerminalOutputOsc8Hyperlink>): Map<String, HyperlinkInfo> {
    if (hyperlinks.isEmpty()) return emptyMap()
    return withContext(Dispatchers.Default) {
      val resolver = uriResolverWrapper.getCompositeResolver()
      val distinctUris = hyperlinks.mapTo(HashSet()) { it.uri }
      buildMap(distinctUris.size) {
        for (uri in distinctUris) {
          resolver.resolve(uri)?.let {
            put(uri, it)
          }
        }
      }
    }
  }
}

private val DELAY: Duration = 500.milliseconds
