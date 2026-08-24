@file:Suppress("UnstableApiUsage")

package com.intellij.python.sdk.frontend.evolution.components

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.impl.ActionMenu
import com.intellij.ide.actions.ShowSettingsUtilImpl
import com.intellij.ide.setToolTipText
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsContexts.PopupTitle
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.python.sdk.frontend.PySdkFrontendBundle
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.GroupHeaderSeparator
import com.intellij.ui.ScreenUtil
import com.intellij.ui.SeparatorWithText
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.fields.ExtendableTextComponent
import com.intellij.ui.components.fields.ExtendableTextField
import com.intellij.ui.popup.WizardPopup
import com.intellij.ui.popup.list.ListPopupImpl
import com.intellij.ui.popup.list.PopupListElementRenderer
import com.intellij.util.IconUtil
import com.intellij.util.ui.GridBag
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.NamedColorUtil
import org.jetbrains.annotations.Nls
import kotlinx.coroutines.CoroutineScope
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Container
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Graphics
import java.awt.GridBagConstraints
import java.awt.Insets
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionAdapter
import javax.swing.Box
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JViewport
import javax.swing.ListCellRenderer
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import javax.swing.event.DocumentEvent

private val SETTINGS_GEAR: Icon = AllIcons.General.GearPlain

/** Distance (unscaled px) from the popup's right edge to the gear icon's right edge, tuned to sit over the `>` column. */
private const val GEAR_RIGHT_INSET = 10

/** Downward nudge (unscaled px) from vertical center, so the gear's optical center lines up with the caption text. */
private const val GEAR_VERTICAL_OFFSET = 1

/** Gap (unscaled px) between a submenu and the left edge of the parent popup it is placed next to. */
private const val SUBMENU_LEFT_GAP = 2

/**
 * Width (in columns) of the add-new name field. Together with the caption beside it, it sets that submenu's width; longer
 * names scroll inside the field rather than widening the popup. This is the one number to turn if the step should be wider
 * or narrower — but the popup shrinks by less than this does, since the caption's width is fixed and the version rows set
 * a floor of their own.
 */
private const val NAME_FIELD_COLUMNS = 14

/**
 * Sample string whose rendered width is reserved for the version column, so a resolved version fits without resize.
 * Wide enough for a `major.minor.patch` plus a pre-release suffix (e.g. `3.15.0rc1`, `3.15.0a0`).
 */
private const val VERSION_RESERVE_SAMPLE = "0.00.00rc0"

/**
 * A group-header separator that also paints a settings gear at its right edge — but only while it is rendering the
 * section whose caption equals [gearCaption] (the "Select Environment" header). A painted separator can't hold real
 * action components, so the gear click is hit-tested and handled in [EvoTreePopup].
 */
private class GearGroupHeaderSeparator(labelInsets: Insets, private val gearCaption: String) : GroupHeaderSeparator(labelInsets) {
  /** True while this reused component is currently rendering the gear-bearing section. */
  val showsGear: Boolean get() = caption == gearCaption

  override fun paintComponent(g: Graphics) {
    super.paintComponent(g)
    if (showsGear) gearIcon().let { icon -> gearBounds().let { icon.paintIcon(this, g, it.x, it.y) } }
  }

  /** The 16px gear scaled down to the caption font's ascent, so it reads at the same size as the header text. */
  private fun gearIcon(): Icon {
    val target = getFontMetrics(font).ascent
    val scale = target.toFloat() / SETTINGS_GEAR.iconHeight
    return if (scale in 0.98f..1.02f) SETTINGS_GEAR else IconUtil.scale(SETTINGS_GEAR, this, scale)
  }

  /**
   * The gear's bounds within this separator: its icon is right-aligned at [GEAR_RIGHT_INSET] so it sits over the rows'
   * `>` arrow column, without measuring the rendered rows. Vertically centered, then nudged down by
   * [GEAR_VERTICAL_OFFSET] to align with the caption text.
   */
  fun gearBounds(): Rectangle {
    val icon = gearIcon()
    val x = width - icon.iconWidth - JBUI.scale(GEAR_RIGHT_INSET)
    val y = (height - icon.iconHeight) / 2 + JBUI.scale(GEAR_VERTICAL_OFFSET)
    return Rectangle(x, y, icon.iconWidth, icon.iconHeight)
  }
}

class EvoPopupListElementRenderer(private val popup: EvoTreePopup) : PopupListElementRenderer<EvoTreeItem>(popup) {
  private val reloadLabel = JLabel()

  // Replace the plain group header with one that paints a settings gear on the "Select Environment" section.
  @Suppress("DEPRECATION") // overrides a platform method that returns the deprecated SeparatorWithText
  override fun createSeparator(): SeparatorWithText {
    val labelInsets = if (ExperimentalUI.isNewUI()) JBUI.CurrentTheme.Popup.separatorLabelInsets()
                      else defaultItemComponentBorder.getBorderInsets(JLabel())
    return GearGroupHeaderSeparator(labelInsets, PySdkFrontendBundle.message("evo.sdk.status.bar.popup.select.environment"))
  }

  // The platform passes a null value during layout measurement, so the params must be nullable.
  override fun customizeComponent(list: JList<out EvoTreeItem>?, value: EvoTreeItem?, isSelected: Boolean) {
    super.customizeComponent(list, value, isSelected)
    if (value != null) {
      // Grey out the whole row (text + icon) when it can't be chosen — including all version rows while the add-new
      // name is invalid. A disabled JLabel dims its icon automatically.
      myTextLabel.isEnabled = value.isEnabled && !popup.isEditingNameInvalid()
      reserveVersionColumn(value)
    }

    // Put a reload icon right next to the submenu arrow for the hovered refreshable tool row (click handled in EvoTreePopup).
    val arrow = myNextStepLabel ?: return
    val buttonPane = arrow.parent as? JComponent ?: return
    val element = value?.element
    val showReload = isSelected && element is EvoTreeLazyNodeElement && element.refreshable && element.state == State.DONE
    buttonPane.removeAll()
    val gb = GridBag()
      .setDefaultFill(GridBagConstraints.BOTH)
      .setDefaultAnchor(GridBagConstraints.CENTER)
      .setDefaultWeightY(1.0)
    // A leading glue takes the slack so the reload icon and the arrow stay packed together at the right.
    buttonPane.add(Box.createHorizontalGlue(), gb.next().weightx(1.0))
    if (showReload) {
      reloadLabel.icon = AllIcons.Actions.Refresh
      buttonPane.add(reloadLabel, gb.next().weightx(0.0))
      // The platform gives the arrow a wide emptyLeft(20) inset; tighten it so the icon sits right by the arrow.
      arrow.border = JBUI.Borders.emptyLeft(4)
    }
    // Every expandable row keeps the platform's standard ">" arrow, even though this popup's submenus always open to the
    // LEFT (see EvoTreePopup.show) — the arrow reads as "has a submenu" rather than as a direction, and matching the
    // platform look everywhere beats being literal about which side it appears on.
    buttonPane.add(arrow, gb.next().weightx(0.0))
  }

  /**
   * Reserve a fixed width for the secondary (version) column on version rows, so the popup is sized correctly on
   * first show and never resizes when the lazily-resolved version arrives (which would otherwise widen the row and
   * push the popup over the widget). Non-version rows keep their natural width. Mirrors the platform's own fixed-width
   * reservation for the mnemonic label.
   */
  private fun reserveVersionColumn(value: EvoTreeItem) {
    val label = secondaryTextLabel() ?: return
    label.preferredSize = null // clear any reservation from a previous row so we read this row's natural size
    if (value.reservesVersionColumn) {
      label.horizontalAlignment = SwingConstants.RIGHT // keep the version right-aligned within the reserved column
      val natural = label.preferredSize
      val reserve = label.getFontMetrics(label.font).stringWidth(VERSION_RESERVE_SAMPLE)
      label.preferredSize = Dimension(maxOf(natural.width, reserve), natural.height)
    }
    else {
      label.horizontalAlignment = SwingConstants.LEADING
    }
  }

  /** The platform's private secondary-text label, reached via the known BorderLayout structure of the row. */
  private fun secondaryTextLabel(): JLabel? {
    val panel = myTextLabel.parent as? JPanel ?: return null
    val secondary = (panel.layout as? BorderLayout)?.getLayoutComponent(BorderLayout.CENTER) as? JPanel ?: return null
    return (secondary.layout as? BorderLayout)?.getLayoutComponent(BorderLayout.EAST) as? JLabel
  }
}

open class EvoTreePopup private constructor(
  aParent: WizardPopup?,
  step: EvoActionPopupStep,
  val myDisposeCallback: Runnable?,
  private val dataContext: DataContext,
  private val maxRowCount: Int,
) : ListPopupImpl(CommonDataKeys.PROJECT.getData(dataContext), aParent, step, null) {
  private val myComponent: Component? = PlatformCoreDataKeys.CONTEXT_COMPONENT.getData(dataContext)

  override fun getListElementRenderer(): ListCellRenderer<*>? {
    return EvoPopupListElementRenderer(this)
  }

  // Keep sub-popups (expanded nodes) EvoTreePopup too, so the custom renderer, speed search and lazy version
  // resolution apply at every level — the platform would otherwise create a plain ListPopupImpl here.
  override fun createPopup(parent: WizardPopup?, step: PopupStep<*>?, parentValue: Any?): WizardPopup {
    if (step is EvoActionPopupStep) {
      return EvoTreePopup(parent, step, null, dataContext, maxRowCount)
    }
    return super.createPopup(parent, step, parentValue)
  }

  /**
   * The platform shows every child popup at its parent's RIGHT edge, flipping to the left only when the screen leaves no
   * room there. This widget sits in the status bar, so submenus always open to the LEFT instead (and their rows are marked
   * with a "<"): flip the requested x here, before the popup is ever painted — [repositionLeftOfParent] alone would show
   * it on the right for one frame. [owner] is the parent popup's content component (see `ListPopupImpl.showNextStepPopup`).
   */
  override fun show(owner: Component, aScreenX: Int, aScreenY: Int, considerForcedXY: Boolean) {
    // The root popup is positioned by the widget, not relative to a parent popup — leave its x alone. `isShowing` also
    // keeps `locationOnScreen` (which throws for a detached component) safe for any caller other than the platform's own.
    val flip = parent != null && owner.isShowing
    val x = if (flip) leftOfX(owner.locationOnScreen.x, preferredContentSize.width) else aScreenX
    super.show(owner, x, aScreenY, considerForcedXY)
  }

  // FINAL_CHOICE is null, and so is the "cannot expand" result of a not-ready/empty node. Hand off to the platform
  // (which expands a real step, or on FINAL_CHOICE closes the popup via disposePopup → getFinalRunnable + disposeAllParents)
  // only for a real step or an actually-chosen leaf; a not-ready node stays open instead of closing or logging a warning.
  override fun handleNextStep(nextStep: PopupStep<*>?, parentValue: Any?, e: InputEvent?): Boolean =
    (nextStep != null || evoStep?.hasPendingFinalAction() == true) && super.handleNextStep(nextStep, parentValue, e)

  private val evoStep: EvoActionPopupStep? get() = listStep as? EvoActionPopupStep

  /** True while this is an add-new submenu whose typed name is invalid (blank/taken) — the renderer greys its version rows. */
  fun isEditingNameInvalid(): Boolean = evoStep?.editableName?.isValid == false

  /**
   * For an editable add-new submenu, stack a name field on top of the version list. The name starts as plain (read-only)
   * text; a pencil icon — or a click on the text — turns it editable. Speed search is off for this step (see
   * [EvoActionPopupStep.isSpeedSearchEnabled]), so once editing, keystrokes reach the field; it writes straight into the
   * shared [EvoEditableName] the version rows read at create time. Called from the [WizardPopup] constructor, so it
   * relies only on the step (already set), not on this class's own fields (not yet initialized).
   */
  override fun createContent(): JComponent {
    val content = super.createContent()
    val editable = evoStep?.editableName ?: return content
    // Right-aligned name, so it sits next to the pencil at the header's right edge and away from the caption on the left.
    val field = ExtendableTextField(editable.value)
    field.isOpaque = false
    field.border = JBUI.Borders.empty(1, 8)   // a touch more compact than the platform default
    field.horizontalAlignment = SwingConstants.RIGHT
    field.columns = NAME_FIELD_COLUMNS
    val defaultForeground = field.foreground
    fun refreshValidity() {
      // An unusable name → red text + a tooltip saying why, and the version rows won't create it (addVersionAction).
      // A tooltip (rather than an extra line) keeps the popup height stable. Both the colour and the message come off the
      // same EvoEditableName.problem, so a row can never look creatable while the tooltip says otherwise.
      val problem = editable.problem
      field.foreground = if (problem == null) defaultForeground else NamedColorUtil.getErrorForeground()
      val hint = when (problem) {
        EvoEditableName.Problem.BLANK -> PySdkFrontendBundle.message("evo.sdk.status.bar.popup.add.new.hint.empty")
        EvoEditableName.Problem.ILLEGAL -> PySdkFrontendBundle.message("evo.sdk.status.bar.popup.add.new.hint.invalid")
        EvoEditableName.Problem.TAKEN -> PySdkFrontendBundle.message("evo.sdk.status.bar.popup.add.new.hint.exists", editable.value)
        null -> null
      }
      field.setToolTipText(hint?.let { HtmlChunk.text(it) })
      list.repaint()   // re-render the version rows so they grey out / un-grey with the name's validity
    }
    field.document.addDocumentListener(object : DocumentAdapter() {
      override fun textChanged(e: DocumentEvent) {
        editable.value = field.text.trim()
        refreshValidity()
      }
    })
    refreshValidity()
    fun setEditing(on: Boolean) {
      if (field.isEditable == on) return
      // Editability is the only thing that changes: the field stays non-opaque either way, so it never paints a text-field
      // background over the header band. Edit mode is visible from the caret and the selection, not from a second shade.
      field.isEditable = on
      editable.editing = on
      if (on) {
        field.requestFocusInWindow()
        field.selectAll()
      }
    }
    // Finish editing = back to plain text AND move focus off the field to the list, so no caret lingers in the label.
    // Defer the focus move so it also wins when triggered from a click on the pencil (which would otherwise re-focus
    // the field as the click completes).
    fun finishEditing() {
      setEditing(false)
      SwingUtilities.invokeLater { list.requestFocusInWindow() }
    }
    setEditing(false)                    // plain text by default
    editable.finishEditing = ::finishEditing
    // The pencil toggles text ↔ edit; clicking the text starts editing; losing focus commits back to text.
    field.addExtension(ExtendableTextComponent.Extension.create(
      AllIcons.Actions.Edit, PySdkFrontendBundle.message("evo.sdk.status.bar.popup.add.new.rename")) {
      if (field.isEditable) finishEditing() else setEditing(true)
    })
    field.addMouseListener(object : MouseAdapter() {
      override fun mouseClicked(e: MouseEvent) {
        if (!field.isEditable) setEditing(true)
      }
    })
    field.addFocusListener(object : FocusAdapter() {
      override fun focusLost(e: FocusEvent) = setEditing(false)
    })
    // A muted caption at the left edge, balancing the right-aligned name: one header line, the caption naming the step and
    // the name showing what will be created. Title case on purpose — it titles this whole "create an environment" step,
    // which after the lone-add-new collapse (see EvoPySdkSwitchPopupFactory) can be all a tool's node shows.
    @Suppress("DialogTitleCapitalization")
    val caption = JBLabel(PySdkFrontendBundle.message("evo.sdk.status.bar.popup.add.new.env.title")).apply {
      foreground = NamedColorUtil.getInactiveTextColor()
      border = JBUI.Borders.emptyLeft(8)
    }
    val header = JPanel(BorderLayout()).apply {
      isOpaque = false
      add(caption, BorderLayout.WEST)
      add(field, BorderLayout.CENTER)
    }
    return JPanel(BorderLayout()).apply {
      // This wrapper is what shows through behind the header (both the header panel and the field are non-opaque), so it
      // has to carry the popup's own background — a default JPanel one paints that band a different shade than the rows.
      background = JBUI.CurrentTheme.Popup.BACKGROUND
      add(header, BorderLayout.NORTH)
      add(content, BorderLayout.CENTER)
    }
  }

  // While the name field is being edited, Enter finishes editing (back to plain text) instead of creating the env; a
  // mouse click on a version still creates it. Enter is bound to selection in ListPopupImpl's input map and dispatched
  // before the field sees it, so we intercept it here (the platform passes the triggering KeyEvent).
  override fun handleSelect(handleFinalChoices: Boolean, e: InputEvent?) {
    val editable = evoStep?.editableName
    if (editable?.editing == true && e is KeyEvent && e.keyCode == KeyEvent.VK_ENTER) {
      editable.finishEditing?.invoke()
      return
    }
    super.handleSelect(handleFinalChoices, e)
  }

  // While the name field is being edited, don't route keys to the list (its Left/Right close the submenu / navigate,
  // Home/End jump rows, etc.). Skipping this lets the un-consumed event fall through to the focused field, which then
  // handles caret movement, selection and editing natively. Non-editing keys are processed by the list as usual.
  override fun process(aEvent: KeyEvent) {
    if (evoStep?.editableName?.editing == true) return
    super.process(aEvent)
  }

  /** Caption of the section header that carries the settings gear. */
  private val selectEnvCaption: String = PySdkFrontendBundle.message("evo.sdk.status.bar.popup.select.environment")

  /** Tooltip shown while hovering the settings gear. */
  private val gearTooltip: @NlsContexts.Tooltip String = PySdkFrontendBundle.message("evo.sdk.status.bar.popup.settings.gear.tooltip")

  init {
    setMaxRowCount(maxRowCount)
    // Submenus (tool nodes and the uv/pip "add new environment" version list) expand on hover.
    isShowSubmenuOnHover = true
  }

  override fun afterShow() {
    super.afterShow()
    // Resolve lazy detail (e.g. interpreter version) for the rows currently in the viewport, and again for any
    // rows scrolled into view later — never for the whole list up front. See [EvoLazyDetail].
    (list.parent as? JViewport)?.addChangeListener { resolveVisibleDetails() }
    resolveVisibleDetails()

    // A click on a tool's inline reload icon re-scans that tool; a click on the "Select Environment" gear opens the
    // Package Managers settings. isActionClick() below stops either from also selecting/expanding a row.
    list.addMouseListener(object : MouseAdapter() {
      override fun mouseReleased(e: MouseEvent) {
        reloadIconItemAt(e.point)?.let { item -> evoStep?.reloadItem(item) }
        if (settingsGearAt(e.point)) openPackageManagersSettings()
      }
    })

    // Two hover targets live in the list's own tooltip: the gear's help text, and the full folder path behind an elided
    // section header. They share one listener because each would otherwise clear the other's tooltip on the next move.
    // The remembered values guard against redundant per-move updates.
    list.addMouseMotionListener(object : MouseMotionAdapter() {
      private var overGear = false
      private var shownTooltip: @Nls String? = null
      override fun mouseMoved(e: MouseEvent) {
        val hit = settingsGearAt(e.point)
        if (hit != overGear) {
          overGear = hit
          list.cursor = if (hit) Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) else Cursor.getDefaultCursor()
        }
        val tooltip = if (hit) gearTooltip else separatorTooltipAt(e.point)
        if (tooltip != shownTooltip) {
          shownTooltip = tooltip
          list.setToolTipText(tooltip?.let { HtmlChunk.text(it) })
        }
      }
    })

    // [show] already placed this submenu to the left using its *preferred* width; correct it now that the laid-out width
    // is known, and clamp it into the screen (a submenu wider than the space on the left would otherwise hang off it).
    repositionLeftOfParent()
  }

  /** Screen x for a submenu [width] px wide that sits entirely left of a parent whose own left edge is [ownerX]. */
  private fun leftOfX(ownerX: Int, width: Int): Int = ownerX - width - JBUI.scale(SUBMENU_LEFT_GAP)

  /** Keeps this submenu to the LEFT of its parent popup, using its real (laid-out) width. */
  private fun repositionLeftOfParent() {
    val parentPopup = parent ?: return
    val self = content
    val parentContent = parentPopup.content
    if (!self.isShowing || !parentContent.isShowing) return
    val target = Rectangle(leftOfX(parentContent.locationOnScreen.x, self.width), self.locationOnScreen.y, self.width, self.height)
    ScreenUtil.moveToFit(target, ScreenUtil.getScreenRectangle(parentContent.locationOnScreen), null)
    setLocation(target.location)
  }

  // Don't let a click on the reload icon or the settings gear select/expand a row — the mouse listener handles them.
  override fun isActionClick(e: MouseEvent): Boolean =
    reloadIconItemAt(e.point) == null && !settingsGearAt(e.point) && super.isActionClick(e)

  /** The tool [EvoTreeItem] whose inline reload icon (only shown on the hovered row) contains [point], or null. */
  private fun reloadIconItemAt(point: Point): EvoTreeItem? {
    val row = list.locationToIndex(point)
    if (row < 0 || row != list.selectedIndex) return null
    val item = list.model.getElementAt(row) as? EvoTreeItem ?: return null
    if (evoStep?.isReloadable(item) != true) return null
    val bounds = reloadIconBounds(row, item) ?: return null
    return if (bounds.contains(point)) item else null
  }

  /** Bounds (in list coordinates) of the reload icon in [row], found by laying out the row's rendered cell. */
  private fun reloadIconBounds(row: Int, item: EvoTreeItem): Rectangle? {
    val cell = list.getCellBounds(row, row) ?: return null
    @Suppress("UNCHECKED_CAST")
    val jList = list as JList<Any?>
    val renderer = jList.cellRenderer ?: return null
    val comp = renderer.getListCellRendererComponent(jList, item, row, true, true) as? JComponent ?: return null
    comp.setBounds(0, 0, cell.width, cell.height)
    layoutRecursively(comp)
    val label = findRefreshLabel(comp) ?: return null
    val topLeft = SwingUtilities.convertPoint(label, 0, 0, comp)
    return Rectangle(cell.x + topLeft.x, cell.y + topLeft.y, label.width, label.height)
  }

  private fun layoutRecursively(c: Component) {
    c.doLayout()
    if (c is Container) c.components.forEach { layoutRecursively(it) }
  }

  private fun findRefreshLabel(c: Component): JLabel? {
    if (c is JLabel && c.icon === AllIcons.Actions.Refresh) return c
    if (c is Container) c.components.forEach { child -> findRefreshLabel(child)?.let { return it } }
    return null
  }

  /**
   * The full folder path to show while [point] is over a section header whose label was elided, or null anywhere else. A
   * header is painted inside the top cell of its section, so this only answers within that separator's own strip — over
   * the row underneath it, the row's own tooltip (if any) applies instead.
   */
  private fun separatorTooltipAt(point: Point): @NlsSafe String? {
    val model = list.model ?: return null
    val row = list.locationToIndex(point)
    if (row < 0 || row >= model.size) return null
    val item = model.getElementAt(row) as? EvoTreeItem ?: return null
    val tooltip = item.separatorTooltip ?: return null
    return if (separatorBounds(row, item)?.contains(point) == true) tooltip else null
  }

  /** Bounds (in list coordinates) of the section header painted at the top of [row]'s cell, or null when it has none. */
  private fun separatorBounds(row: Int, item: EvoTreeItem): Rectangle? {
    val cell = list.getCellBounds(row, row) ?: return null
    @Suppress("UNCHECKED_CAST")
    val jList = list as JList<Any?>
    val renderer = jList.cellRenderer ?: return null
    val comp = renderer.getListCellRendererComponent(jList, item, row, false, false) as? JComponent ?: return null
    comp.setBounds(0, 0, cell.width, cell.height)
    layoutRecursively(comp)
    val separator = findGearSeparator(comp) ?: return null
    val topLeft = SwingUtilities.convertPoint(separator, 0, 0, comp)
    return Rectangle(cell.x + topLeft.x, cell.y + topLeft.y, separator.width, separator.height)
  }

  /** True if [point] hits the settings gear painted on the "Select Environment" separator (in that section's top cell). */
  private fun settingsGearAt(point: Point): Boolean {
    val model = list.model ?: return false
    val row = list.locationToIndex(point)
    if (row < 0 || row >= model.size) return false
    val item = model.getElementAt(row) as? EvoTreeItem ?: return false
    return item.separatorAbove?.text == selectEnvCaption && settingsGearBounds(row, item)?.contains(point) == true
  }

  /** Bounds (in list coordinates) of the settings gear on [row]'s "Select Environment" separator, or null. */
  private fun settingsGearBounds(row: Int, item: EvoTreeItem): Rectangle? {
    val cell = list.getCellBounds(row, row) ?: return null
    @Suppress("UNCHECKED_CAST")
    val jList = list as JList<Any?>
    val renderer = jList.cellRenderer ?: return null
    val comp = renderer.getListCellRendererComponent(jList, item, row, false, false) as? JComponent ?: return null
    comp.setBounds(0, 0, cell.width, cell.height)
    layoutRecursively(comp)
    val sep = findGearSeparator(comp)?.takeIf { it.showsGear } ?: return null
    val gb = sep.gearBounds()
    val topLeft = SwingUtilities.convertPoint(sep, gb.x, gb.y, comp)
    return Rectangle(cell.x + topLeft.x, cell.y + topLeft.y, gb.width, gb.height)
  }

  private fun findGearSeparator(c: Component): GearGroupHeaderSeparator? {
    if (c is GearGroupHeaderSeparator) return c
    if (c is Container) c.components.forEach { child -> findGearSeparator(child)?.let { return it } }
    return null
  }

  private fun openPackageManagersSettings() {
    val project = CommonDataKeys.PROJECT.getData(dataContext) ?: return
    cancel() // close the popup before showing the (modal) settings dialog
    // Select the Package Managers page by its configurable id (findById), not by display name.
    ShowSettingsUtilImpl.showSettingsDialog(project, "python.package.managers.group.settings", null)
  }

  /** Triggers lazy detail resolution for every [EvoLazyDetail] leaf currently visible in the list's viewport. */
  private fun resolveVisibleDetails() {
    val model = list.model ?: return
    val first = list.firstVisibleIndex
    val last = list.lastVisibleIndex
    if (first < 0 || last < first) return
    for (i in first..last) {
      if (i >= model.size) break
      val action = (model.getElementAt(i) as? EvoTreeItem)?.let { (it.element as? EvoTreeLeafElement)?.action }
      // The row already reserves space for the version (see EvoPopupListElementRenderer), so a repaint is enough to
      // paint the resolved text into that reserved column — no popup resize.
      (action as? EvoLazyDetail)?.resolveOnFocus { list.repaint() }
    }
  }


  constructor(
    parentPopup: WizardPopup?,
    title: @PopupTitle String?,
    evoTreeNodeElement: EvoTreeNodeElement,
    dataContext: DataContext,
    scope: CoroutineScope,
    maxRowCount: Int,
    disposeCallback: Runnable?,
  ) : this(
    aParent = parentPopup,
    step = EvoActionPopupStep(
      myTitle = title,
      node = evoTreeNodeElement,
      dataContext = dataContext,
      scope = scope,
    ),
    myDisposeCallback = disposeCallback,
    dataContext = dataContext,
    maxRowCount = maxRowCount
  )


  override fun dispose() {
    myDisposeCallback?.run()
    ActionMenu.showDescriptionInStatusBar(true, myComponent, null)
    super.dispose()
  }

}


