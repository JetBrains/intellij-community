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
  private val node: EvoTreeNodeElement,
  private val dataContext: DataContext,
  private val scope: CoroutineScope,
) : ListPopupStepEx<EvoTreeItem> {
  private val listeners: MutableList<ListPopupStep.ListPopupModelListener> = arrayListOf()

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
  }

  /**
   * Loads every element not loaded yet. A leaf's load only marks it [State.DONE] — but that is what [isSelectable]
   * requires, so rows swapped in later (see [toggleExpanded]) are inert until this has run over them.
   */
  private fun loadNewElements() {
    val project = CommonDataKeys.PROJECT.getData(dataContext) ?: return
    node.sections.forEach { section ->
      section.elements.filter { it.state == State.CREATED }.forEach { it.load(project, scope, listeners) }
    }
  }

  /** The collapsed/expanded views of this step's version list, or null when it is not an add-new step. */
  val versionRows: EvoVersionRows? get() = node.versionRows

  /**
   * A child step listing [alternatives] as its rows — the "…" menu of a row that stands for several choices.
   *
   * Built here rather than in the popup so it inherits this step's data context and scope: the rows it produces run the
   * same creation the parent row would have run, only with a different interpreter, and that closure was captured when
   * the parent row was built.
   */
  fun alternativesStep(alternatives: EvoAlternatives): EvoActionPopupStep {
    val node = EvoTreeStaticNodeElement(alternatives.alternativesTitle, AllIcons.Language.Python,
                                        listOf(EvoTreeSection(elements = alternatives.alternatives)))
    return EvoActionPopupStep(alternatives.alternativesTitle, node, dataContext, scope)
  }

  /**
   * Expands the version list into its individual interpreters, or collapses it back.
   *
   * Only the node's sections are updated — no listener is notified and nothing is loaded — because the caller rebuilds
   * the popup around this node rather than refreshing the open one, and the step built for the new popup loads its own
   * elements. See `EvoTreePopup.toggleExpandedAndReopen`.
   */
  fun toggleExpanded() {
    val versionRows = node.versionRows ?: return
    versionRows.toggle()
    node.sections.clear()
    node.sections.addAll(versionRows.sections())
  }

  override fun addListener(listener: ListPopupStep.ListPopupModelListener) {
    listeners.add(listener)
  }

  /** The chosen leaf's action, queued to run once the popup has closed (see [getFinalRunnable]); null for a node. */
  private var finalRunnable: Runnable? = null

  /** The editable env-name holder for an add-new submenu, or null when the name is fixed — see [EvoTreeNodeElement]. */
  val editableName: EvoEditableName? get() = node.editableName

  /** The name to show in this submenu's header when it cannot be edited (hatch's declared env) — see [EvoTreeNodeElement]. */
  val fixedName: String? get() = node.fixedName

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
        // In an add-new submenu with an invalid name (blank/taken) the version rows are inert: don't select or close —
        // keep the popup open so the user can fix the name (the field is red with an explaining tooltip).
        if (editableName?.isValid == false) return null
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

  /**
   * The row's tooltip. This is the one the user sees: [javax.swing.JList.getToolTipText] asks the renderer first and
   * only falls back to the list's own, so anything the popup sets on the list is ignored wherever a row answers here.
   */
  override fun getTooltipTextFor(value: EvoTreeItem?): @NlsContexts.Tooltip String? {
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
    // An invalid add-new name makes its version rows non-selectable (not just no-op) so nav/hover/click can't pick them.
    editableName?.isValid != false && value != null &&
    // A row reporting a failure is selectable so its output can be reached — and it has to be, since the platform
    // delivers a click only to the row that is already selected (`ListPopupImpl.MyList.processMouseEvent`), leaving an
    // unselectable row unclickable however precisely it is hit. It still reads as disabled: its presentation stays so,
    // and `hasSubstep` keeps the submenu arrow off it.
    (value.opensProcessOutput || (value.element.state == State.DONE && value.isEnabled))

  override fun getIconFor(value: EvoTreeItem?): Icon? = value?.icon

  override fun getTextFor(value: EvoTreeItem?): @ListItem String = value?.text ?: ""

  override fun getSecondaryTextFor(value: EvoTreeItem?): @Nls String? = value?.secondaryText

  override fun getSecondaryIconFor(t: EvoTreeItem?): @Nls Icon? = when (t?.element?.state) {
    State.LOADING -> AnimatedIcon.Default.INSTANCE
    State.ERROR -> AllIcons.General.Error
    // A tool that is simply unavailable, or answered with nothing, is not a failure — but it did not work either, so it
    // gets a sign of its own rather than looking like an ordinary disabled row with nothing to say.
    State.NOT_AVAILABLE -> AllIcons.General.Warning
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

  // Off for an editable add-new submenu, so typed characters reach its name field instead of the list's speed search.
  override fun isSpeedSearchEnabled(): Boolean = editableName == null

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
