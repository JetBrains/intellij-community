@file:Suppress("UnstableApiUsage")

package com.intellij.python.sdk.frontend.evolution.components

import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.ex.ActionUtil.lastUpdateAndCheckDumb
import com.intellij.openapi.actionSystem.ex.ActionUtil.performActionDumbAwareWithCallbacks
import com.intellij.openapi.actionSystem.ex.ActionUtil.updateAction
import com.intellij.openapi.ui.popup.ListPopupStep
import com.intellij.openapi.ui.popup.ListPopupStepEx
import com.intellij.openapi.ui.popup.ListSeparator
import com.intellij.openapi.ui.popup.MnemonicNavigationFilter
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.SpeedSearchFilter
import com.intellij.python.sdk.frontend.PySdkFrontendBundle
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
  /** The node whose rows this step shows. Read by [EvoTreePopup] to reopen a panel over the node it was built from. */
  val node: EvoTreeNodeElement,
  private val dataContext: DataContext,
  private val scope: CoroutineScope,
) : ListPopupStepEx<EvoTreeItem> {
  private val listeners: MutableList<ListPopupStep.ListPopupModelListener> = arrayListOf()

  /**
   * Takes in the rows [node]'s loader swaps in after this step was built.
   *
   * A tool node's submenu can be opened while the node still loads (it shows an [EvoTreeMessageLeafElement] until then),
   * so this step is not the one that started the load and would otherwise never hear that the real rows arrived. They
   * are loaded first, since [isSelectable] holds nothing selectable until they are, and only then is the list repainted.
   */
  private val onNodeRowsArrived = ListPopupStep.ListPopupModelListener {
    loadNewElements()
    listeners.forEach { it.onModelChanged() }
  }

  init {
    // Rows that gate themselves (the package-manager actions) get their own update() run against this popup's data
    // context — which carries the project's dependency file — so their presentation is truthful before anything is
    // painted; getValues() then drops the ones that reported themselves invisible.
    node.sections.asSequence()
      .flatMap { it.elements }
      .filterIsInstance<EvoTreeActionLeafElement>()
      .forEach { element ->
        // The event carries the element's own presentation (not a copy), so update() writes straight into the row.
        val event = AnActionEvent.createEvent(dataContext, element.presentation, ActionPlaces.POPUP, ActionUiKind.POPUP, null)
        updateAction(element.action, event)
      }
    loadNewElements()
    node.addModelListener(onNodeRowsArrived)
  }

  /**
   * The popup this step served is being disposed — `ListPopupImpl.dispose` removes itself here — so stop holding it
   * through [node]. The tree outlives the popup: the widget reuses it on the next open.
   */
  override fun removeListener(listener: ListPopupStep.ListPopupModelListener) {
    listeners.remove(listener)
    node.removeModelListener(onNodeRowsArrived)
  }

  /**
   * Loads every element not loaded yet. A leaf's load only marks it [State.DONE] — but that is what [isSelectable]
   * requires, so a row swapped in later is inert until this has run over it.
   */
  private fun loadNewElements() {
    val project = CommonDataKeys.PROJECT.getData(dataContext) ?: return
    node.sections.forEach { section ->
      section.elements.filter { it.state == State.CREATED }.forEach { it.load(project, scope, listeners) }
    }
  }

  /**
   * The panel shown while [node] reloads: one "Loading…" row, over a node of its own.
   *
   * Built here rather than in the popup so it inherits this step's data context and scope. It is a separate node so the
   * reloading one keeps its rows and its controls; see [loadingNodeElement].
   */
  fun loadingStep(): EvoActionPopupStep = EvoActionPopupStep(null, loadingNodeElement(node), dataContext, scope)

  /**
   * A child step over [node] — the picker a row's inline icon opens.
   *
   * Built here for the same reason [loadingStep] is: it inherits this step's data context and scope.
   */
  fun childStep(node: EvoTreeNodeElement): EvoActionPopupStep = EvoActionPopupStep(null, node, dataContext, scope)

  override fun addListener(listener: ListPopupStep.ListPopupModelListener) {
    listeners.add(listener)
  }

  /** The chosen leaf's action, queued to run once the popup has closed (see [getFinalRunnable]); null for a node. */
  private var finalRunnable: Runnable? = null

  /** The caption above this submenu's header, or null for the "add new" wording — see [EvoTreeNodeElement]. */
  val headerCaption: @Nls String? get() = node.headerCaption

  /** What this submenu says its rows do, shown along its bottom — see [EvoTreeNodeElement]. */
  val stepDescription: @Nls String? get() = node.stepDescription


  override fun onChosen(
    selectedValue: EvoTreeItem,
    finalChoice: Boolean
  ): PopupStep<*>? {
    finalRunnable = null
    // A row that only reports a failure: the one thing to do with it is see what happened. Queued like a leaf's action
    // so the tool window opens once the popup has closed, rather than behind it.
    if (selectedValue.opensProcessOutput) {
      val showOutput = selectedValue.showOutput
      finalRunnable = Runnable { showOutput?.invoke() }
      return PopupStep.FINAL_CHOICE
    }
    if (!node.isEnabled) return PopupStep.FINAL_CHOICE
    // A disabled row that [isSelectable] admits for its inline icon: selecting it must do nothing at all. Only the icon
    // acts on such a row, and running the row's own action here would select the environment the icon rebuilds.
    if (!selectedValue.isEnabled) return null

    return when (val element = selectedValue.element) {
      // Only open a submenu for a loaded, non-empty node — an empty popup crashes Swing layout (AIOOBE 0). The
      // "add new environment" node is handled here too; EvoTreePopup repositions its submenu to the left.
      is EvoTreeNodeElement ->
        if (element.isEnabled && element.hasContent()) {
          // Only a static node opts in; a lazy one reports itself from its loader instead, so this cannot double-count.
          (element as? EvoTreeStaticNodeElement)?.onOpened?.invoke()
          EvoActionPopupStep(null, element, dataContext, scope)
        }
        else null
      is EvoTreeLeafElement -> {
        // A picker's row changes what the row behind it says and touches nothing else, so it runs here and now and the
        // popup stays open — see [EvoTreeNodeElement.picksWithoutClosing]. Returning no step leaves this popup showing;
        // the action itself asks the popup to drop back to the list.
        if (node.picksWithoutClosing) {
          performActionItem(element, null)
          return null
        }
        // Otherwise run the action only after the whole popup closes (via getFinalRunnable), so a tool window or dialog
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

  /**
   * Set while the pointer is over a control painted *inside* a row's cell — the section header's gear.
   *
   * Such a control has its own tooltip, which the popup puts on the list, and the row would otherwise answer over it:
   * see [getTooltipTextFor]. Read live, since the tooltip is asked for on each hover rather than cached.
   */
  @Volatile
  var rowTooltipSuppressed: Boolean = false

  /**
   * The row's tooltip. This is the one the user sees: [javax.swing.JList.getToolTipText] asks the renderer first and
   * only falls back to the list's own, so anything the popup sets on the list is ignored wherever a row answers here.
   */
  override fun getTooltipTextFor(value: EvoTreeItem?): @NlsContexts.Tooltip String? {
    if (rowTooltipSuppressed) return null
    val tooltip = value?.tooltip ?: return null
    // A row that can open its process output says so, since nothing else about it suggests it is a control.
    val text = if (value.opensProcessOutput) PySdkFrontendBundle.message("evo.sdk.status.bar.popup.failure.tooltip", tooltip)
    else tooltip
    return multiLineTooltip(text)
  }

  override fun setEmptyText(emptyText: StatusText) {}

  override fun getValues(): List<EvoTreeItem> =
    node.sections.flatMap { section ->
      // A self-gating action that reported itself inapplicable is dropped before indexing, so the separator still
      // lands on the first row actually shown.
      val elements = section.elements.filter { it !is EvoTreeActionLeafElement || it.presentation.isVisible }
      // A section's header is painted into its first row's cell, so only that row carries the separator and its tooltip.
      elements.mapIndexed { index, element ->
        EvoTreeItem(element, section.label?.takeIf { index == 0 }, section.labelTooltip?.takeIf { index == 0 })
      }
    }

  // set to true if we need actions '...' on disabled items too
  override fun isSelectable(value: EvoTreeItem?): Boolean =
    value != null &&
    // A row reporting a failure is selectable so its output can be reached — and it has to be, since the platform
    // delivers a click only to the row that is already selected (`ListPopupImpl.MyList.processMouseEvent`), leaving an
    // unselectable row unclickable however precisely it is hit. It still reads as disabled: its presentation stays so,
    // and `hasSubstep` keeps the submenu arrow off it.
    //
    // A row offering a rebuild is selectable for the same reason: the inline icon is painted on the hovered row and
    // hit-tested there, so a row nothing can select can carry no icon. An environment another tool owns is such a row —
    // disabled here because adopting it would type its SDK to the wrong tool, and still rebuildable by its owner. See
    // [onChosen], which keeps choosing a disabled row inert.
    (value.opensProcessOutput || value.basePythonPanel != null || (value.isReady && value.isEnabled))

  override fun getIconFor(value: EvoTreeItem?): Icon? = value?.icon

  override fun getTextFor(value: EvoTreeItem?): @ListItem String = value?.text ?: ""

  override fun getSecondaryTextFor(value: EvoTreeItem?): @Nls String? = value?.secondaryText

  /**
   * The spinner, and only the spinner: the platform draws this straight after the row's text, which is where a row that
   * is working belongs. A row that *failed* says so in the trailing column instead, lined up with the submenu arrows —
   * see [EvoTreeItem.statusIcon].
   */
  override fun getSecondaryIconFor(t: EvoTreeItem?): @Nls Icon? =
    // Not every loading row carries the spinner: a tool node reports its load inside its submenu — see [showsLoader].
    if (t?.showsLoader == true) AnimatedIcon.Default.INSTANCE else null

  override fun getSeparatorAbove(value: EvoTreeItem?): ListSeparator? = value?.separatorAbove

  override fun getDefaultOptionIndex(): Int = 0

  override fun getTitle(): @PopupTitle String? = myTitle

  // The platform passes a null value during layout measurement, so the param must be nullable.
  override fun isFinal(value: EvoTreeItem?): Boolean {
    value ?: return true
    // A picker's rows leave the popup open, so none of them ends the walk through it.
    if (node.picksWithoutClosing) return false
    // to make ... actions menu even for non-disabled items all steps have to be final
    return value.element is EvoTreeLeafElement || !value.isReady
  }

  // The platform passes a null value during layout measurement, so the param must be nullable. Offer a submenu only
  // for a loaded, non-empty node — otherwise an empty child popup can crash Swing layout.
  override fun hasSubstep(selectedValue: EvoTreeItem?): Boolean {
    val element = selectedValue?.element as? EvoTreeNodeElement ?: return false
    return selectedValue.isSubstepSuppressed && selectedValue.isReady && element.hasContent()
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
