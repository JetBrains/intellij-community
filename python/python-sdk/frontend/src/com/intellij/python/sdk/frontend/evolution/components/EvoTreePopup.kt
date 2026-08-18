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
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.python.sdk.frontend.PySdkFrontendBundle
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.GroupHeaderSeparator
import com.intellij.ui.SeparatorWithText
import com.intellij.ui.popup.WizardPopup
import com.intellij.ui.popup.list.ListPopupImpl
import com.intellij.ui.popup.list.PopupListElementRenderer
import com.intellij.util.IconUtil
import com.intellij.util.ui.GridBag
import com.intellij.util.ui.JBUI
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
import java.awt.event.InputEvent
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

private val SETTINGS_GEAR: Icon = AllIcons.General.GearPlain

/** Distance (unscaled px) from the popup's right edge to the gear icon's right edge, tuned to sit over the `>` column. */
private const val GEAR_RIGHT_INSET = 10

/** Downward nudge (unscaled px) from vertical center, so the gear's optical center lines up with the caption text. */
private const val GEAR_VERTICAL_OFFSET = 1

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

class EvoPopupListElementRenderer(listPopupImpl: ListPopupImpl) : PopupListElementRenderer<EvoTreeItem>(listPopupImpl) {
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
      myTextLabel.isEnabled = value.isEnabled
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

  // FINAL_CHOICE is null, and so is the "cannot expand" result of a not-ready/empty node. Hand off to the platform
  // (which expands a real step, or on FINAL_CHOICE closes the popup via disposePopup → getFinalRunnable + disposeAllParents)
  // only for a real step or an actually-chosen leaf; a not-ready node stays open instead of closing or logging a warning.
  override fun handleNextStep(nextStep: PopupStep<*>?, parentValue: Any?, e: InputEvent?): Boolean =
    (nextStep != null || evoStep?.hasPendingFinalAction() == true) && super.handleNextStep(nextStep, parentValue, e)

  private val evoStep: EvoActionPopupStep? get() = listStep as? EvoActionPopupStep

  /** Caption of the section header that carries the settings gear. */
  private val selectEnvCaption: String = PySdkFrontendBundle.message("evo.sdk.status.bar.popup.select.environment")

  /** Tooltip shown while hovering the settings gear. */
  private val gearTooltip: @NlsContexts.Tooltip String = PySdkFrontendBundle.message("evo.sdk.status.bar.popup.settings.gear.tooltip")

  init {
    setMaxRowCount(maxRowCount)
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

    // Over the gear: show its tooltip and a hand cursor. The popup wires no per-row tooltips and uses the default
    // cursor for rows, so toggling both on the list is safe. A boolean guards against redundant per-move updates.
    list.addMouseMotionListener(object : MouseMotionAdapter() {
      private var overGear = false
      override fun mouseMoved(e: MouseEvent) {
        val hit = settingsGearAt(e.point)
        if (hit == overGear) return
        overGear = hit
        list.setToolTipText(if (hit) HtmlChunk.text(gearTooltip) else null)
        list.cursor = if (hit) Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) else Cursor.getDefaultCursor()
      }
    })
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


