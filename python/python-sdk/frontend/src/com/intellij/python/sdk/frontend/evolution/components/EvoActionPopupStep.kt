@file:Suppress("UnstableApiUsage")

package com.intellij.python.sdk.frontend.evolution.components

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.ex.ActionUtil.lastUpdateAndCheckDumb
import com.intellij.openapi.actionSystem.ex.ActionUtil.performActionDumbAwareWithCallbacks
import com.intellij.openapi.ui.popup.ListPopupStep
import com.intellij.openapi.ui.popup.ListPopupStepEx
import com.intellij.openapi.ui.popup.ListSeparator
import com.intellij.openapi.ui.popup.MnemonicNavigationFilter
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.SpeedSearchFilter
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

  /** The chosen leaf's action, queued to run once the popup has closed (see [getFinalRunnable]); null for a node. */
  private var finalRunnable: Runnable? = null

  /** True when this step renders the "add new environment" node's submenu — the popup positions it to the left. */
  val isAddNewSubmenu: Boolean get() = node is EvoTreeAddNewNode

  override fun onChosen(
    selectedValue: EvoTreeItem,
    finalChoice: Boolean
  ): PopupStep<*>? {
    finalRunnable = null
    if (!node.isEnabled) return PopupStep.FINAL_CHOICE

    return when (val element = selectedValue.element) {
      // Only open a submenu for a loaded, non-empty node — an empty popup crashes Swing layout (AIOOBE 0). The
      // "add new environment" node is handled here too; EvoTreePopup repositions its submenu to the left.
      is EvoTreeNodeElement ->
        if (element.isEnabled && element.hasContent()) EvoActionPopupStep(null, element, dataContext, scope) else null
      is EvoTreeLeafElement -> {
        // Run the action only after the whole popup closes (via getFinalRunnable), so a tool window or dialog
        // it opens never appears behind a still-visible popup. FINAL_CHOICE is null; see EvoTreePopup.handleNextStep.
        finalRunnable = Runnable { performActionItem(element, null) }
        PopupStep.FINAL_CHOICE
      }
    }
  }

  /** True when the last [onChosen] chose a leaf and queued its action — so the popup should close (and then run it). */
  fun hasPendingFinalAction(): Boolean = finalRunnable != null

  /** True if [item] is a refreshable tool node (shows an inline reload icon). */
  fun isReloadable(item: EvoTreeItem?): Boolean = (item?.element as? EvoTreeLazyNodeElement)?.refreshable == true

  /** Force-reloads (re-scans, bypassing the backend cache) just the tool of [item] — its reload icon was clicked. */
  fun reloadItem(item: EvoTreeItem) {
    val node = item.element as? EvoTreeLazyNodeElement ?: return
    val project = CommonDataKeys.PROJECT.getData(dataContext) ?: return
    node.reload(project, scope, listeners)
  }

  override fun getTooltipTextFor(value: EvoTreeItem?): @NlsContexts.Tooltip String? = value?.tooltip

  override fun setEmptyText(emptyText: StatusText) {}

  override fun getValues(): List<EvoTreeItem> =
    node.sections.flatMap { section ->
      section.elements.mapIndexed { index, element -> EvoTreeItem(element, section.label?.takeIf { index == 0 }) }
    }

  // set to true if we need actions '...' on disabled items too
  override fun isSelectable(value: EvoTreeItem?): Boolean = value != null && value.element.state == State.DONE && value.isEnabled

  override fun getIconFor(value: EvoTreeItem?): Icon? = value?.icon

  override fun getTextFor(value: EvoTreeItem?): @ListItem String = value?.text ?: ""

  override fun getSecondaryTextFor(value: EvoTreeItem?): @Nls String? = value?.secondaryText

  override fun getSecondaryIconFor(t: EvoTreeItem?): @Nls Icon? = when (t?.element?.state) {
    State.LOADING -> AnimatedIcon.Default.INSTANCE
    State.ERROR -> AllIcons.General.Error
    else -> null
  }

  override fun getSeparatorAbove(value: EvoTreeItem?): ListSeparator? = value?.separatorAbove

  override fun getDefaultOptionIndex(): Int = 0

  override fun getTitle(): @PopupTitle String? = myTitle

  // The platform passes a null value during layout measurement, so the param must be nullable.
  override fun isFinal(value: EvoTreeItem?): Boolean {
    value ?: return true
    // to make ... actions menu even for non-disabled items all steps have to be final
    return value.element is EvoTreeLeafElement || value.element.state != State.DONE
  }

  // The platform passes a null value during layout measurement, so the param must be nullable. Offer a submenu only
  // for a loaded, non-empty node — otherwise an empty child popup can crash Swing layout.
  override fun hasSubstep(selectedValue: EvoTreeItem?): Boolean {
    val element = selectedValue?.element as? EvoTreeNodeElement ?: return false
    return selectedValue.isSubstepSuppressed && element.state == State.DONE && element.hasContent()
  }

  override fun canceled() {}

  override fun isMnemonicsNavigationEnabled(): Boolean = false

  override fun getMnemonicNavigationFilter(): MnemonicNavigationFilter<EvoTreeItem?>? = null

  override fun isSpeedSearchEnabled(): Boolean = true

  // Filter the list as you type — match the row title and (once resolved) its secondary text.
  override fun getSpeedSearchFilter(): SpeedSearchFilter<EvoTreeItem?> = SpeedSearchFilter { value ->
    value?.let { listOfNotNull(it.text, it.secondaryText).joinToString(" ") } ?: ""
  }

  override fun isAutoSelectionEnabled(): Boolean = false

  override fun getFinalRunnable(): Runnable? = finalRunnable

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
