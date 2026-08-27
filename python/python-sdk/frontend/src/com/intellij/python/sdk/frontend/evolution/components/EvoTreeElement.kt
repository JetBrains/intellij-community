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
import com.intellij.openapi.util.NlsContexts.PopupTitle
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.python.sdk.common.evolution.EvoRpcFailedException
import com.intellij.python.sdk.frontend.PySdkFrontendBundle
import com.intellij.util.PathUtil
import java.util.concurrent.CopyOnWriteArrayList
import javax.swing.Icon
import kotlinx.coroutines.CoroutineScope
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
class EvoTreeUnavailableLeafElement(action: AnAction, reason: @Nls String) : EvoTreeLeafElement(action) {
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
 * Mutable holder for the edited env name, shared between the add-new submenu's name field and its version actions.
 * [takenNames] are the names already in use in this tool node (existing envs). While the name has a [problem] the field
 * shows it in red with an explaining tooltip and the version rows refuse to create anything.
 */
class EvoEditableName(@Volatile @NlsSafe var value: String, val takenNames: Set<String> = emptySet()) {
  /** Why a name cannot back a new environment. One enum, so the field's hint and [isValid] can never disagree. */
  enum class Problem { BLANK, ILLEGAL, TAKEN }

  /** True while the name field is in edit mode, so the popup treats Enter as "finish editing" instead of "create env". */
  @Volatile
  var editing: Boolean = false

  /** Set by the name field: switches it back to read-only text (invoked by the popup when Enter is pressed while editing). */
  var finishEditing: (() -> Unit)? = null

  /**
   * What disqualifies the current name, or null when it is usable. [Problem.ILLEGAL] defers to the platform's own rule set
   * (`PathUtilRt`): no path separators, no `.`/`..` traversal, no control or Windows-invalid characters, no reserved device
   * name. That is what keeps the name a single path segment, so the environment always lands directly inside the parent
   * folder the tool picked and the name can never steer it elsewhere.
   */
  val problem: Problem?
    get() = when {
      value.isBlank() -> Problem.BLANK
      !PathUtil.isValidFileName(value) -> Problem.ILLEGAL
      value in takenNames -> Problem.TAKEN
      else -> null
    }

  /** The current name can back a new environment. */
  val isValid: Boolean get() = problem == null
}

/**
 * Implemented by a leaf [AnAction] that stands for more than one concrete choice: an "add new environment" Python
 * version on a machine carrying several installs of it.
 *
 * The row keeps doing its own thing when clicked — it acts on the default choice — and offers the rest behind an inline
 * "…" that [EvoTreePopup] paints on it while it is hovered, so the finer choice costs nothing to a user who does not
 * want it. An implementation with fewer than two [alternatives] is offering no choice at all, and the "…" is not painted.
 */
interface EvoAlternatives {
  val alternativesTitle: @PopupTitle String

  /**
   * The rows the menu shows. Built by whoever built the row they hang off, so they are the very same rows the expanded
   * list would show — same titles, same lazily-resolved version column — rather than a second rendering of the same
   * data that could drift from it.
   */
  val alternatives: List<EvoTreeLeafElement>
}

/**
 * A row that reveals more of the list rather than selecting an environment, drawn in the platform's link colour.
 *
 * The colour is the whole of it: such a row is an ordinary leaf, laid out like any other, and its own icon says which
 * way the list is about to grow. It keeps the hover highlight and the keyboard, which a painted strip would not.
 */
interface EvoLinkRow

/**
 * The two views of an "add new environment" version list, and which of them is showing.
 *
 * *Collapsed* is one row per Python version — the version is the choice, and the interpreter backing it is the IDE's
 * pick. *Expanded* turns each version into a section header and lists that version's actual installs beneath it, so the
 * interpreter becomes the choice. Both are built up front from the same options, because the toggle has to be instant:
 * it is a way of looking at a list the user is already reading, not a new request.
 *
 * Which one is showing lives here, in the object itself, which is built once per popup tree and held by the node. So the
 * choice survives a close-and-reopen for as long as that tree is reused, and a rebuilt tree — a expired cache, a
 * reloaded tool — starts collapsed again. That is the intended lifetime: it is a way of looking at one list of
 * environments, not a setting about the IDE.
 *
 * [canExpand] is false when no version has a second install, and then the toggle is not offered at all, since expanding
 * would only put a header above each row already shown.
 */
class EvoVersionRows(
  private val collapsed: List<EvoTreeSection>,
  private val expanded: List<EvoTreeSection>,
) {
  val canExpand: Boolean get() = expanded.isNotEmpty()

  var isExpanded: Boolean = false
    private set

  fun sections(): List<EvoTreeSection> = if (isExpanded && canExpand) expanded else collapsed

  fun toggle() {
    isExpanded = !isExpanded
  }
}

/**
 * The "add new environment" node: a normal expandable node whose submenu lists the Python versions of [versionRows] — so
 * the platform handles mouse and keyboard natively. It is marked so [EvoTreePopup] can reposition its submenu to the
 * *left* of the parent (the platform opens submenus to the right). The row shows the pre-filled env name. When
 * [editableName] is set, the submenu also shows a name field on top (and turns off speed search) so the user can rename
 * before creating. [secondaryText] fills the row's version column (an env that doesn't exist yet has no version, so
 * "n/a"), keeping it aligned with the sibling rows that do show one.
 */
class EvoTreeAddNewNode(
  text: @Nls String,
  icon: Icon,
  versionRows: EvoVersionRows,
  editableName: EvoEditableName? = null,
  fixedName: @NlsSafe String? = null,
  secondaryText: @Nls String? = null,
) : EvoTreeNodeElement(text, icon) {
  init {
    sections.addAll(versionRows.sections())
    this.versionRows = versionRows
    this.editableName = editableName
    this.fixedName = fixedName
    secondaryText?.let { presentation.putClientProperty(ActionUtil.SECONDARY_TEXT, it) }
  }
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
   * The env-name field this node's submenu shows above its rows, or null when it has none. Set by [EvoTreeAddNewNode],
   * and inherited by a tool node that absorbed a lone "add new" child (see [EvoLoadedNode]) — the popup renders the
   * field from whichever node it is showing, so the holder has to travel with the sections.
   */
  var editableName: EvoEditableName? = null

  /**
   * The name this node's submenu shows in its header when there is nothing to edit — hatch's declared environment, named
   * in `pyproject.toml` and not ours to rename. Null when the node has an [editableName] instead, or no header at all.
   */
  var fixedName: @NlsSafe String? = null

  /**
   * The collapsed/expanded views of this node's version list, or null when it has none. Set by [EvoTreeAddNewNode] and,
   * like [editableName], inherited by a tool node that absorbed a lone "add new" child — the toggle is rendered from
   * whichever node the popup is showing, so it has to travel with the sections.
   */
  var versionRows: EvoVersionRows? = null

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
  val editableName: EvoEditableName? = null,
  /** See [EvoTreeNodeElement.fixedName] — carried for the same reason [editableName] is. */
  val fixedName: @NlsSafe String? = null,
  val versionRows: EvoVersionRows? = null,
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
   * Leaves this node's own rows, and the [versionRows]/[editableName]/[fixedName] that describe them, exactly as they
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
            editableName = loaded.editableName
            fixedName = loaded.fixedName
            versionRows = loaded.versionRows
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
