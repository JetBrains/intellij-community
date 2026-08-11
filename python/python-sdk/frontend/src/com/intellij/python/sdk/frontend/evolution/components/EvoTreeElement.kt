package com.intellij.python.sdk.frontend.evolution.components

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.ListPopupStep
import com.intellij.openapi.ui.popup.ListSeparator
import com.intellij.openapi.util.NlsActions.ActionDescription
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.python.sdk.frontend.PySdkFrontendBundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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

sealed class EvoTreeNodeElement(
  text: String,
  icon: Icon,
) : EvoTreeElement(Presentation(text)) {
  val sections = mutableListOf<EvoTreeSection>()

  init {
    presentation.icon = icon
  }
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

class EvoTreeLazyNodeElement(
  text: String,
  icon: Icon,
  val loader: suspend () -> List<EvoTreeSection>,
) : EvoTreeNodeElement(text, icon) {
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

  override fun load(project: Project, scope: CoroutineScope, listeners: List<ListPopupStep.ListPopupModelListener>) {
    scope.launch(Dispatchers.IO) {
      loadMutex.withLock {
        updateState(State.LOADING, listeners)
        sections.clear()
        try {
          val elements = withBackgroundProgress(project, PySdkFrontendBundle.message("evolution.progress.title.loading", presentation.text), true) {
            loader.invoke()
          }
          sections.addAll(elements)
          presentation.isEnabled = true
          updateState(State.DONE, listeners)
        }
        catch (warning: EvoWarningException) {
          presentation.isEnabled = false
          presentation.putClientProperty(ActionUtil.TOOLTIP_TEXT, warning.message)
          updateState(State.NOT_AVAILABLE, listeners)
        }
        catch (error: Exception) {
          presentation.isEnabled = false
          presentation.putClientProperty(ActionUtil.TOOLTIP_TEXT, error.message)
          updateState(State.ERROR, listeners)
        }
      }
    }
  }
}
