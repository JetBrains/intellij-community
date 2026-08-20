package com.intellij.python.sdk.frontend.evolution.components

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.ListPopupStep
import com.intellij.openapi.ui.popup.ListSeparator
import com.intellij.openapi.util.NlsActions.ActionDescription
import com.intellij.openapi.util.NlsSafe
import com.intellij.util.PathUtil
import org.jetbrains.annotations.Nls
import com.intellij.openapi.application.EDT
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.python.sdk.frontend.PySdkFrontendBundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.swing.Icon

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
 * The "add new environment" node: a normal expandable node whose submenu lists the Python [versions] — so the platform
 * handles mouse and keyboard natively. It is marked so [EvoTreePopup] can reposition its submenu to the *left* of the
 * parent (the platform opens submenus to the right). The row shows the pre-filled env name. When [editableName] is set,
 * the submenu also shows a name field on top (and turns off speed search) so the user can rename before creating.
 * [secondaryText] fills the row's version column (an env that doesn't exist yet has no version, so "n/a"), keeping it
 * aligned with the sibling rows that do show one.
 */
class EvoTreeAddNewNode(
  text: @Nls String,
  icon: Icon,
  versions: List<EvoTreeLeafElement>,
  editableName: EvoEditableName? = null,
  secondaryText: @Nls String? = null,
) : EvoTreeNodeElement(text, icon) {
  init {
    sections.add(EvoTreeSection(elements = versions))
    this.editableName = editableName
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
class EvoLoadedNode(val sections: List<EvoTreeSection>, val refreshable: Boolean, val editableName: EvoEditableName? = null)

class EvoTreeLazyNodeElement(
  text: String,
  icon: Icon,
  val loader: suspend (forceRefresh: Boolean) -> EvoLoadedNode,
) : EvoTreeNodeElement(text, icon) {
  /** Set from the last load: true once the backend measured this tool as slow, so it shows an inline reload icon. */
  var refreshable: Boolean = false
    private set

  init {
    presentation.isEnabled = false
  }

  private fun updateState(state: State, listeners: List<ListPopupStep.ListPopupModelListener>) {
    this.state = state
    this.presentation.putClientProperty(
      ActionUtil.SECONDARY_TEXT,
      when (state) {
        State.LOADING -> "Loading..."
        else -> null
      }
    )
    presentation.putClientProperty(ActionUtil.HIDE_DROPDOWN_ICON, true)
    presentation.isEnabled = state == State.DONE
    listeners.forEach { it.onModelChanged() }
  }

  override fun load(project: Project, scope: CoroutineScope, listeners: List<ListPopupStep.ListPopupModelListener>): Unit =
    load(project, scope, listeners, forceRefresh = false)

  /** Reloads bypassing any backend cache (the tool's reload icon), so a long-cached tool (conda) re-scans. */
  fun reload(project: Project, scope: CoroutineScope, listeners: List<ListPopupStep.ListPopupModelListener>): Unit =
    load(project, scope, listeners, forceRefresh = true)

  private fun load(project: Project, scope: CoroutineScope, listeners: List<ListPopupStep.ListPopupModelListener>, forceRefresh: Boolean) {
    scope.launch(Dispatchers.IO) {
      loadMutex.withLock {
        // The [sections] list backs the popup's Swing model (via EvoActionPopupStep.getValues) and updateState fires
        // ListPopupModelListener.onModelChanged. Both must run on the EDT — mutating the model off-EDT corrupts the
        // list UI (transient duplicated rows, wrong size, AIOOBE in WideSelectionListUI). Only the loader runs on IO.
        withContext(Dispatchers.EDT) { updateState(State.LOADING, listeners) }
        try {
          val loaded = withBackgroundProgress(project, PySdkFrontendBundle.message("evolution.progress.title.loading", presentation.text), true) {
            loader.invoke(forceRefresh)
          }
          withContext(Dispatchers.EDT) {
            refreshable = loaded.refreshable
            editableName = loaded.editableName
            // Swap in the new data only once it's ready, so an open submenu never flashes empty during a reload.
            sections.clear()
            sections.addAll(loaded.sections)
            presentation.isEnabled = true
            updateState(State.DONE, listeners)
          }
        }
        catch (warning: EvoWarningException) {
          withContext(Dispatchers.EDT) {
            presentation.isEnabled = false
            presentation.putClientProperty(ActionUtil.TOOLTIP_TEXT, warning.message)
            updateState(State.NOT_AVAILABLE, listeners)
          }
        }
        catch (error: Exception) {
          withContext(Dispatchers.EDT) {
            presentation.isEnabled = false
            presentation.putClientProperty(ActionUtil.TOOLTIP_TEXT, error.message)
            updateState(State.ERROR, listeners)
          }
        }
      }
    }
  }
}
