package com.intellij.python.sdk.ui.evolution.ui.components

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.ListPopupStep
import com.intellij.openapi.ui.popup.ListSeparator
import com.intellij.openapi.util.NlsActions.ActionDescription
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.python.sdk.ui.evolution.sdk.EvoWarning
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.PyError
import com.jetbrains.python.onFailure
import com.jetbrains.python.onSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.swing.Icon

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
  sections: List<EvoTreeSection>
) : EvoTreeNodeElement(text, icon) {
  init {
    this.sections.addAll(sections)
  }
}

class EvoTreeLazyNodeElement(
  text: String,
  icon: Icon,
  val loader: suspend () -> Result<List<EvoTreeSection>, PyError>
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
        //State.ERROR -> "⚠\uFE0F" // ⚠️ ⚠
        //State.NOT_AVAILABLE -> "n/a" // ⚠ 💾💔
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

        val result = withBackgroundProgress(project, "Loading ${presentation.text}", true) {
          loader.invoke()
        }
        result.onSuccess { elements ->
          sections.addAll(elements)
          presentation.isEnabled = true
          updateState(State.DONE, listeners)
        }.onFailure { error ->
          presentation.isEnabled = false
          presentation.putClientProperty(ActionUtil.TOOLTIP_TEXT, error.message)
          val state = when (error) {
            is EvoWarning -> State.NOT_AVAILABLE
            else -> State.ERROR
          }
          updateState(state, listeners)
        }
      }
    }
  }
}

