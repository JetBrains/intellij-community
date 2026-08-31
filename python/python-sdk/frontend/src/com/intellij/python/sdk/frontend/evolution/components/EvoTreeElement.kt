package com.intellij.python.sdk.frontend.evolution.components

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.python.sdk.common.evolution.EvoNodeStats
import com.intellij.python.sdk.common.evolution.PyEvoWidgetCollector
import com.intellij.openapi.ui.popup.ListPopupStep
import com.intellij.openapi.ui.popup.ListSeparator
import com.intellij.openapi.util.NlsActions.ActionDescription
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.python.sdk.common.evolution.EvoRpcFailedException
import com.intellij.python.sdk.frontend.PySdkFrontendBundle
import java.util.concurrent.CopyOnWriteArrayList
import javax.swing.Icon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.Nls

/** Failure of a lazy node [EvoTreeLazyNodeElement] loader; a warning is a soft "n/a", anything else an error. */
internal sealed class EvoLoadException(message: String) : Exception(message)
internal class EvoWarningException(message: String) : EvoLoadException(message)
internal class EvoErrorException(message: String) : EvoLoadException(message)

enum class State { CREATED, LOADING, DONE, ERROR, NOT_AVAILABLE }

/** [labelTooltip] is the full text behind an elided [label] (a section's folder path), shown when the header is hovered. */
data class EvoTreeSection(
  val label: ListSeparator? = null,
  val elements: List<EvoTreeElement>,
  val labelTooltip: @NlsSafe String? = null,
) {
  constructor(label: ListSeparator? = null, vararg elements: EvoTreeElement) : this(label = label, elements = elements.toList())
}

sealed class EvoTreeElement(
  val presentation: Presentation,
  var state: State = State.CREATED,
) {
  val loadMutex = Mutex()

  /**
   * The steps showing this element, told whenever its rows or its state change.
   *
   * A step registers here because it is not always the step that started the load: a tool node's submenu can be opened
   * while the node still loads, and the step that shows the "Loading…" row must be told when the real rows arrive.
   */
  private val modelListeners = CopyOnWriteArrayList<ListPopupStep.ListPopupModelListener>()

  fun addModelListener(listener: ListPopupStep.ListPopupModelListener) {
    modelListeners.add(listener)
  }

  fun removeModelListener(listener: ListPopupStep.ListPopupModelListener) {
    modelListeners.remove(listener)
  }

  protected fun fireModelChanged(listeners: List<ListPopupStep.ListPopupModelListener>) {
    listeners.forEach { it.onModelChanged() }
    modelListeners.forEach { it.onModelChanged() }
  }

  val description: @ActionDescription String?
    get() = presentation.description

  val isEnabled: Boolean
    get() = presentation.isEnabled

  open fun load(project: Project, scope: CoroutineScope, listeners: List<ListPopupStep.ListPopupModelListener>) {
    state = State.DONE
  }
}

open class EvoTreeLeafElement(
  val action: AnAction,
  presentation: Presentation = action.templatePresentation,
) : EvoTreeElement(presentation)

/**
 * A leaf that cannot be acted on, and says why: disabled, with [reason] as its tooltip, instead of looking selectable
 * and failing only once clicked.
 *
 * A disabled presentation is already enough to make the row unselectable (see `EvoActionPopupStep.isSelectable`), so it
 * stays in [State.DONE] and carries no sign. The sign belongs to a tool node that could not answer, where the row is the
 * only place the failure can be reported; here the row is one environment among the many the node listed, and a column
 * of warning signs beside a list of environments reads as a problem with the list.
 */
class EvoTreeUnavailableLeafElement(action: AnAction, reason: @Nls String) : EvoTreeLeafElement(
  action = action,
  // A copy, not the action's own template. The platform refuses a write to a template presentation, which is shared by
  // every place the action is shown, and disabling one row must not disable the action everywhere else it appears. The
  // copy already carries what the action set up for itself, since that runs before this.
  presentation = action.templatePresentation.clone(),
) {
  init {
    presentation.isEnabled = false
    presentation.putClientProperty(ActionUtil.TOOLTIP_TEXT, reason)
  }
}

/**
 * A row that only says something, and cannot be chosen: a tool node's own rows are not there to show.
 *
 * A node holds one of these saying "Loading…" until its loader answers, and one carrying the reason when that loader
 * fails or has nothing to offer. The load is reported here, one level in, instead of on the node's own row: a spinner
 * and a "Loading…" on every tool of the main list made each open of the widget look busy and cluttered (PY-91873),
 * while the node the user actually wants is one click away in any case. A submenu also cannot open empty, because the
 * platform crashes laying out a popup with no row, so this row is what makes the node openable before it is loaded.
 * [EvoTreeLazyNodeElement] replaces it in place, so the submenu the user already opened fills in rather than reopening.
 *
 * [state] is [State.LOADING] for the "Loading…" row, which is what gives it the spinner, and [State.DONE] for a reason.
 * Neither is [State.CREATED], so no step ever "loads" one: only the node that owns it retires it.
 */
class EvoTreeMessageLeafElement(
  message: @Nls String,
  state: State,
) : EvoTreeLeafElement(
  action = object : AnAction() {
    override fun actionPerformed(e: AnActionEvent) = Unit
  },
  presentation = Presentation(message),
) {
  init {
    presentation.isEnabled = false
    this.state = state
  }
}

/**
 * A throwaway node whose submenu is one "Loading…" row and nothing else — the panel shown while a node reloads.
 *
 * Independent of the node being reloaded on purpose. That node keeps its rows and its controls, so the real submenu can
 * be rebuilt complete once the load ends, and this panel carries no header or footer of its own because it has none of
 * the state that draws them.
 */
internal fun loadingNodeElement(reloading: EvoTreeNodeElement): EvoTreeStaticNodeElement =
  EvoTreeStaticNodeElement(
    text = reloading.presentation.text ?: "",
    icon = reloading.presentation.icon ?: AllIcons.Actions.Refresh,
    sections = listOf(loadingSection()),
  )

/** The "Loading…" row a tool node shows until its loader answers — see [EvoTreeMessageLeafElement]. */
private fun loadingSection(): EvoTreeSection =
  EvoTreeSection(elements = listOf(EvoTreeMessageLeafElement(
    PySdkFrontendBundle.message("evo.sdk.status.bar.popup.node.loading"), State.LOADING)))

/**
 * A leaf whose action decides for itself whether it applies: [EvoActionPopupStep] runs the action's own `update()`
 * against the popup's data context before the list is shown, and drops the row when the action reports itself
 * invisible. Used for the package-manager rows, which are shared platform actions gated on the project's dependency
 * file — unlike the widget's own inline actions, each of which is built for exactly the row it sits on and needs no
 * update. Its presentation is a private copy, since a shared action's template must not be written to.
 */
class EvoTreeActionLeafElement(action: AnAction) : EvoTreeLeafElement(action, action.templatePresentation.clone())

/**
 * A row that folds part of the list away or unfolds it, rather than naming an environment.
 *
 * Drawn quieter and smaller than the rows it controls, so a list is read as environments with a control under them
 * instead of as environments one of which is worded oddly.
 */
interface EvoDisclosureRow

/**
 * A node whose submenu is a list of Pythons — so the platform handles mouse and keyboard natively. It is marked so
 * [EvoTreePopup] can reposition its submenu to the *left* of the parent (the platform opens submenus to the right).
 * [secondaryText] fills the row's own version column, keeping it aligned with the sibling rows that show one.
 */
class EvoTreeAddNewNode(
  text: @Nls String,
  icon: Icon,
  sections: List<EvoTreeSection>,
  secondaryText: @Nls String? = null,
  headerCaption: @Nls String? = null,
  stepDescription: @Nls String? = null,
) : EvoTreeNodeElement(text, icon) {
  init {
    this.sections.addAll(sections)
    this.headerCaption = headerCaption
    this.stepDescription = stepDescription
    secondaryText?.let { presentation.putClientProperty(ActionUtil.SECONDARY_TEXT, it) }
  }
}

/**
 * Implemented by a leaf [AnAction] whose row offers the Pythons its environment can be built on, opened by the right
 * button over that row.
 *
 * Picking one acts at once: it creates the environment the row stands for, or rebuilds the one it names.
 */
interface EvoBasePythonPanel {
  val basePythonPanel: EvoTreeNodeElement?
}

/**
 * Implemented by a leaf [AnAction] whose secondary detail (e.g. the interpreter version) is resolved lazily when
 * its row is focused, so the popup never probes every environment up front. The popup invokes [resolveOnFocus]
 * on selection change; the action updates its own presentation and calls [onResolved] (on EDT) to repaint.
 */
interface EvoLazyDetail {
  fun resolveOnFocus(onResolved: () -> Unit)
}

sealed class EvoTreeNodeElement(
  text: String,
  icon: Icon,
) : EvoTreeElement(Presentation(text)) {
  val sections = mutableListOf<EvoTreeSection>()

  /**
   * The caption above this node's submenu header, or null for the "add new" wording every such header used to carry.
   * Travels with the sections, so a node that absorbed a child keeps that child's heading.
   */
  var headerCaption: @Nls String? = null


  /**
   * One line under this node's submenu saying what picking a row there does, or null for a panel that says nothing.
   *
   * Every inner panel carries one: a row's own label says which environment or which Python, never what choosing it
   * will do to the project. Travels with the sections for the same reason [headerCaption] does.
   */
  var stepDescription: @Nls String? = null

  init {
    presentation.icon = icon
  }

  /** Whether this node has any leaf to show — a guard against opening an empty submenu (which crashes Swing layout). */
  fun hasContent(): Boolean = sections.any { it.elements.isNotEmpty() }
}

class EvoTreeStaticNodeElement(
  text: String,
  icon: Icon,
  sections: List<EvoTreeSection>,
  /**
   * Run when this node's submenu is opened, for a node that wants to know.
   *
   * A static node is built from data already in hand, so it has no [EvoTreeLazyNodeElement.loader] to hang a
   * "was opened" signal off — and without this it is indistinguishable from never having been touched.
   */
  val onOpened: (() -> Unit)? = null,
) : EvoTreeNodeElement(text, icon) {
  init {
    this.sections.addAll(sections)
  }
}

/**
 * Result of a lazy node's [EvoTreeLazyNodeElement.loader]: its sections, whether the backend measured it slow, and the
 * name field its submenu should show — the last one set only when the loader collapsed a lone "add new" row into these
 * sections, so that step keeps the field (and its caption) the absorbed row would have shown.
 */
class EvoLoadedNode(
  val sections: List<EvoTreeSection>,
  val refreshable: Boolean,
  /** See [EvoTreeNodeElement.headerCaption] — carried so a node that absorbed a child keeps that child's heading. */
  val headerCaption: @Nls String? = null,
  /** See [EvoTreeNodeElement.stepDescription] — carried for the same reason [headerCaption] is. */
  val stepDescription: @Nls String? = null,
)

class EvoTreeLazyNodeElement(
  text: String,
  icon: Icon,
  /**
   * What usage statistics report this node as: its kind, plus the backing tool's `fusId` when it has one. Taken from
   * the node's DTO rather than derived from [text], which is a translated label and must never reach a metric.
   */
  val nodeStats: EvoNodeStats,
  /**
   * Opens the process output for this node's last run, for when the row reports a failure. Null when the node has no
   * process behind it at all.
   */
  val showOutput: (() -> Unit)? = null,
  /**
   * Whether a node that answered with nothing carries the warning sign — see [EvoTreeItem.statusIcon].
   *
   * False for a node whose "nothing" is an ordinary answer rather than a problem to report.
   */
  val signsUnavailable: Boolean = true,
  val loader: suspend (forceRefresh: Boolean) -> EvoLoadedNode,
) : EvoTreeNodeElement(text, icon) {
  /** Set from the last load: true once the backend measured this tool as slow, so it shows an inline reload icon. */
  var refreshable: Boolean = false
    private set

  init {
    // The row is openable from the start, and its submenu says "Loading…" until the loader answers. See
    // [EvoTreeMessageLeafElement] for why the load is reported there and not here.
    sections.add(loadingSection())
  }

  /**
   * Runs [onFinished] once, when the load now in flight ends — however it ends.
   *
   * The listener retires itself, so a node that is reloaded again gets a fresh one rather than a growing pile. A state
   * change to [State.LOADING] is the start of that load, not its end, so it is skipped.
   */
  fun whenLoadFinished(onFinished: () -> Unit) {
    addModelListener(object : ListPopupStep.ListPopupModelListener {
      override fun onModelChanged() {
        if (state == State.LOADING) return
        removeModelListener(this)
        onFinished()
      }
    })
  }

  private fun updateState(state: State, listeners: List<ListPopupStep.ListPopupModelListener>) {
    this.state = state
    presentation.putClientProperty(ActionUtil.HIDE_DROPDOWN_ICON, true)
    // A tool that failed, or answered with nothing, is disabled and carries the sign. One that is still loading stays
    // enabled, so the user can open it and read the load there.
    presentation.isEnabled = state != State.ERROR && state != State.NOT_AVAILABLE
    fireModelChanged(listeners)
  }

  override fun load(project: Project, scope: CoroutineScope, listeners: List<ListPopupStep.ListPopupModelListener>): Unit =
    load(project, scope, listeners, forceRefresh = false)

  /**
   * Reloads bypassing any backend cache (the tool's reload icon), so a long-cached tool (conda) re-scans.
   *
   * Leaves this node's own rows exactly as they
   * are. [EvoTreePopup] shows a panel of its own while the load runs ([loadingNodeElement]) and opens the real submenu
   * once it ends, so the state here is never half-replaced: emptying it cost poetry its expand/collapse control, which
   * the submenu builds once when it opens and cannot add later.
   */
  fun reload(project: Project, scope: CoroutineScope, listeners: List<ListPopupStep.ListPopupModelListener>): Unit =
    load(project, scope, listeners, forceRefresh = true)

  /**
   * Reports how this node's load ended, whatever the ending. Every branch below routes through here, so a tool that
   * fails or answers with nothing is as visible in the data as one that works — the whole point of the metric.
   */
  private fun reportLoad(
    project: Project,
    outcome: PyEvoWidgetCollector.NodeOutcome,
    forceRefresh: Boolean,
    startedAt: Long,
  ) = PyEvoWidgetCollector.nodeExpanded(
    project = project,
    node = nodeStats,
    outcome = outcome,
    isReload = forceRefresh,
    wasSlow = refreshable,
    durationMs = (System.nanoTime() - startedAt) / 1_000_000,
  )

  private fun load(project: Project, scope: CoroutineScope, listeners: List<ListPopupStep.ListPopupModelListener>, forceRefresh: Boolean) {
    scope.launch(Dispatchers.IO) {
      loadMutex.withLock {
        val startedAt = System.nanoTime()
        // The [sections] list backs the popup's Swing model (via EvoActionPopupStep.getValues) and updateState fires
        // ListPopupModelListener.onModelChanged. Both must run on the EDT — mutating the model off-EDT corrupts the
        // list UI (transient duplicated rows, wrong size, AIOOBE in WideSelectionListUI). Only the loader runs on IO.
        // A first load is reported inside the submenu, on the "Loading…" row this node is built holding. A reload keeps
        // its rows and is reported in a panel of its own instead — see [reload]. Reporting either on the node's own row
        // put a spinner on the widget list, which PY-91873 removed.
        withContext(Dispatchers.EDT) { updateState(State.LOADING, listeners) }
        try {
          val loaded = withBackgroundProgress(project, PySdkFrontendBundle.message("evolution.progress.title.loading", presentation.text), true) {
            loader.invoke(forceRefresh)
          }
          withContext(Dispatchers.EDT) {
            refreshable = loaded.refreshable
            // Kept unless the loader brought its own, which happens when it absorbed a lone "add new" row and with it
            // that row's header. A tool node is otherwise titled at construction, before any of this ran.
            headerCaption = loaded.headerCaption ?: headerCaption
            stepDescription = loaded.stepDescription ?: stepDescription
            // Swap in the new data only once it's ready, so an open submenu goes straight from "Loading…" to the
            // rows and never flashes empty.
            sections.clear()
            sections.addAll(loaded.sections)
            updateState(State.DONE, listeners)
          }
          reportLoad(project, PyEvoWidgetCollector.NodeOutcome.OK, forceRefresh, startedAt)
        }
        catch (warning: EvoWarningException) {
          withContext(Dispatchers.EDT) {
            presentation.putClientProperty(ActionUtil.TOOLTIP_TEXT, warning.message)
            showReason(warning.message)
            updateState(State.NOT_AVAILABLE, listeners)
          }
          // The tool answered, it just had nothing to offer — not a failure, and counted separately from one.
          reportLoad(project, PyEvoWidgetCollector.NodeOutcome.EMPTY, forceRefresh, startedAt)
        }
        // Two ways loading a node can fail: the backend reported it ([EvoErrorException]), or the backend could not be
        // asked at all. Anything else is a bug here and propagates instead of becoming a tooltip.
        catch (error: EvoLoadException) {
          showLoadError(error, listeners)
          reportLoad(project, PyEvoWidgetCollector.NodeOutcome.ERROR, forceRefresh, startedAt)
        }
        catch (error: EvoRpcFailedException) {
          showLoadError(error, listeners)
          reportLoad(project, PyEvoWidgetCollector.NodeOutcome.ERROR, forceRefresh, startedAt)
        }
        // The floor under every branch above. A load that leaves by any other route — the progress this runs under is
        // cancellable, and a loader can always throw something no branch names — must not leave the node loading: the
        // widget keeps its tree across opens, so such a node holds a "Loading…" row that nothing will ever replace,
        // for the life of the tree rather than for this open. Put back as never loaded, so opening it asks again.
        finally {
          if (state == State.LOADING) {
            // The scope this ran in may be cancelled already, and the state still has to be put back.
            withContext(NonCancellable + Dispatchers.EDT) { updateState(State.CREATED, listeners) }
          }
        }
      }
    }
  }

  /**
   * True while the only rows this node has are its own messages — it has never loaded any environment.
   *
   * Such a node has a "Loading…" row to replace, and a submenu opened over it would otherwise spin for good. One that
   * did load environments keeps them through a failed reload, so what worked last time stays readable; the row itself
   * carries the sign and the reason.
   */
  private val showsMessageOnly: Boolean
    get() = sections.all { section -> section.elements.all { it is EvoTreeMessageLeafElement } }

  /** Puts [reason] where the "Loading…" row was, for a node that has no environment of its own to show instead. */
  private fun showReason(reason: @Nls String?) {
    if (!showsMessageOnly) return
    val message = reason ?: PySdkFrontendBundle.message("evo.sdk.status.bar.popup.node.failed")
    sections.clear()
    sections.add(EvoTreeSection(elements = listOf(EvoTreeMessageLeafElement(message, State.DONE))))
  }

  /** Renders a failed load as a disabled row whose tooltip carries the reason. */
  private suspend fun showLoadError(error: Throwable, listeners: List<ListPopupStep.ListPopupModelListener>) {
    withContext(Dispatchers.EDT) {
      presentation.putClientProperty(ActionUtil.TOOLTIP_TEXT, error.message)
      showReason(error.message)
      updateState(State.ERROR, listeners)
    }
  }
}
