@file:Suppress("UnstableApiUsage")

package com.intellij.python.sdk.ui.evolution.ui.components

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil.lastUpdateAndCheckDumb
import com.intellij.openapi.actionSystem.ex.ActionUtil.performActionDumbAwareWithCallbacks
import com.intellij.openapi.ui.popup.*
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsContexts.ListItem
import com.intellij.openapi.util.NlsContexts.PopupTitle
import com.intellij.ui.AnimatedIcon
import com.intellij.util.ui.StatusText
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.Nls
import java.awt.event.InputEvent
import javax.swing.Icon

open class EvoActionPopupStep(
  private val myTitle: @PopupTitle String?,
  private val node: EvoTreeNodeElement,
  private val dataContext: DataContext,
  private val scope: CoroutineScope,
) : ListPopupStepEx<EvoTreeItem> {
  private val listeners: MutableList<ListPopupStep.ListPopupModelListener> = arrayListOf()

  init {
    CommonDataKeys.PROJECT.getData(dataContext)?.let { project ->
      node.sections.forEach { section ->
        section.elements.filter { it.state == State.CREATED }.forEach { it.load(project, scope, listeners) }
      }
    }
  }

  override fun addListener(listener: ListPopupStep.ListPopupModelListener) {
    listeners.add(listener)
  }

  override fun onChosen(
    selectedValue: EvoTreeItem,
    finalChoice: Boolean
  ): PopupStep<*>? {
    if (!node.isEnabled) return PopupStep.FINAL_CHOICE

    val step = when (val element = selectedValue.element) {
      is EvoTreeNodeElement -> {
        when {
          element.isEnabled -> {
            EvoActionPopupStep(
              null, //element.presentation.text,
              element,
              dataContext,
              scope
            )
          }
          else -> null
          //  finalChoice == false {
          //  EvoActionPopupStep(
          //    "Settings of ${element.presentation.text}",
          //    element,
          //    dataContext,
          //    scope
          //  )
          //}
        }
      }
      is EvoTreeLeafElement -> {
        performActionItem(element, null)
        PopupStep.FINAL_CHOICE
      }
    }
    val result = step

    return result
  }

  override fun getTooltipTextFor(value: EvoTreeItem): @NlsContexts.Tooltip String? = value.tooltip

  override fun setEmptyText(emptyText: StatusText) {}

  override fun getValues(): List<EvoTreeItem> {
    val result = node.sections.flatMap { section ->
      val items = section.elements.mapIndexed { index, element ->
        EvoTreeItem(element, section.label?.takeIf { index == 0 })
      }
      items
    }
    return result
  }

  // set to true if we need actions '...' on disabled items too
  override fun isSelectable(value: EvoTreeItem): Boolean = value.element.state == State.DONE && value.isEnabled

  override fun getIconFor(value: EvoTreeItem): Icon? = value.icon

  override fun getTextFor(value: EvoTreeItem): @ListItem String = value.text

  override fun getSecondaryTextFor(value: EvoTreeItem): @Nls String? = value.secondaryText

  override fun getSecondaryIconFor(t: EvoTreeItem): @Nls Icon? = when(t.element.state) {
    State.LOADING -> AnimatedIcon.Default.INSTANCE
    State.ERROR -> AllIcons.General.Error
    else -> null
  }

  override fun getSeparatorAbove(value: EvoTreeItem): ListSeparator? = value.separatorAbove

  override fun getDefaultOptionIndex(): Int = 0

  override fun getTitle(): @PopupTitle String? = myTitle

  override fun isFinal(value: EvoTreeItem): Boolean {
    // to make ... actions menu even for non-disabled items all steps have to be final
    //return true
    return value.element is EvoTreeLeafElement || value.element.state != State.DONE
  }

  override fun hasSubstep(selectedValue: EvoTreeItem): Boolean {
    return selectedValue.isSubstepSuppressed && selectedValue.element is EvoTreeNodeElement // && selectedValue.element.state == State.DONE
  }

  override fun canceled() {}

  override fun isMnemonicsNavigationEnabled(): Boolean = false

  override fun getMnemonicNavigationFilter(): MnemonicNavigationFilter<EvoTreeItem?>? = null

  override fun isSpeedSearchEnabled(): Boolean = false

  override fun getSpeedSearchFilter(): SpeedSearchFilter<EvoTreeItem?>? = null

  override fun isAutoSelectionEnabled(): Boolean = false

  override fun getFinalRunnable(): Runnable? = null

  fun performActionItem(item: EvoTreeLeafElement, inputEvent: InputEvent?) {
    val action = item.action
    val event = createAnActionEvent(item, inputEvent)
    event.setInjectedContext(action.isInInjectedContext)
    if (lastUpdateAndCheckDumb(action, event, false)) {
      performActionDumbAwareWithCallbacks(action, event)
    }
  }

  fun createAnActionEvent(item: EvoTreeElement, inputEvent: InputEvent?): AnActionEvent {
    val presentation = item.presentation.clone()
    return AnActionEvent.createEvent(dataContext, presentation, ActionPlaces.POPUP, ActionUiKind.POPUP, inputEvent)
  }
}
