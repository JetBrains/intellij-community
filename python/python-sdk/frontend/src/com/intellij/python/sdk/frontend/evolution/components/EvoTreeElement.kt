package com.intellij.python.sdk.frontend.evolution.components

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.ListPopupStep
import com.intellij.openapi.ui.popup.ListSeparator
import com.intellij.openapi.util.NlsActions.ActionDescription
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

data class EvoTreeSection(val label: ListSeparator? = null, val elements: List<EvoTreeElement>) {
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

class EvoTreeLeafElement(val action: AnAction) : EvoTreeElement(action.templatePresentation)

/**
 * The uv/pip "add new environment" node: a normal expandable node whose submenu lists the Python [versions] — so the
 * platform handles mouse and keyboard natively. It is marked so [EvoTreePopup] can reposition its submenu to the
 * *left* of the parent (the platform opens submenus to the right). The row shows the auto-generated env folder name.
 */
class EvoTreeAddNewNode(
  text: @Nls String,
  icon: Icon,
  versions: List<EvoTreeLeafElement>,
) : EvoTreeNodeElement(text, icon) {
  init {
    sections.add(EvoTreeSection(elements = versions))
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

/** Result of a lazy node's [EvoTreeLazyNodeElement.loader]: its sections plus whether the backend measured it slow. */
class EvoLoadedNode(val sections: List<EvoTreeSection>, val refreshable: Boolean)

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
