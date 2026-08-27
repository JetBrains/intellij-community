@file:Suppress("UnstableApiUsage")

package com.intellij.python.sdk.frontend.evolution.components

import com.intellij.codeInsight.hint.HintUtil
import com.intellij.icons.AllIcons
import com.intellij.ide.actions.ShowSettingsUtilImpl
import com.intellij.ide.setToolTipText
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.impl.ActionMenu
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsContexts.PopupTitle
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.python.sdk.common.evolution.EvoNodeKind
import com.intellij.python.sdk.common.evolution.EvoNodeStats
import com.intellij.python.sdk.common.evolution.PyEvoWidgetCollector
import com.intellij.python.sdk.frontend.PySdkFrontendBundle
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.GroupHeaderSeparator
import com.intellij.ui.ScreenUtil
import com.intellij.ui.SeparatorWithText
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.fields.ExtendableTextComponent
import com.intellij.ui.components.fields.ExtendableTextField
import com.intellij.ui.paint.RectanglePainter
import com.intellij.ui.popup.WizardPopup
import com.intellij.ui.popup.list.ListPopupImpl
import com.intellij.ui.popup.list.PopupListElementRenderer
import com.intellij.util.IconUtil
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.GridBag
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.NamedColorUtil
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Container
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
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
import javax.swing.border.Border
import javax.swing.border.EmptyBorder
import javax.swing.event.DocumentEvent
import javax.swing.event.ListSelectionEvent
import javax.swing.event.ListSelectionListener
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.Nls

private val SETTINGS_GEAR: Icon = AllIcons.General.GearPlain

/** Distance (unscaled px) from the popup's right edge to the gear icon's right edge, tuned to sit over the `>` column. */
private const val GEAR_RIGHT_INSET = 10

/** Padding (unscaled px) around the gear icon that the hover fill covers — the button's surface beyond the glyph. */
private const val GEAR_HOVER_PADDING: Int = 3

/** Corner radius (unscaled px) of the gear's hover fill, matching the platform's action-button rounding. */
private const val GEAR_HOVER_ARC: Int = 4

/** Downward nudge (unscaled px) from vertical center, so the gear's optical center lines up with the caption text. */
private const val GEAR_VERTICAL_OFFSET = 1

/** Clear space (unscaled px) between the header caption and the gear, so the two never touch — a word space. */
private const val GEAR_CAPTION_GAP = 6

/** Gap (unscaled px) between a submenu and the left edge of the parent popup it is placed next to. */
private const val SUBMENU_LEFT_GAP = 2

/**
 * Columns of text reserved for the add-new name: how much of a long name stays visible, and how much room there is to
 * type one. The field itself is sized to its content up to this (see [EvoTreePopup.nameHeader]), so editing never has to
 * widen the popup. Together with the caption beside it, this sets that submenu's minimum width; the version rows set a
 * floor of their own.
 */
private const val NAME_FIELD_COLUMNS = 18

/**
 * Gap between the header's caption and the name beside it — a word space, since the two are read as one phrase
 * ("Choose base Python for default"). The field brings no left inset of its own, so the whole gap comes from here, and
 * anything tighter runs the caption's last word into a lowercase name.
 */
private const val NAME_FIELD_GAP = 5

/** Slack past a name's measured width, so a character's overhang is never clipped — see [EvoTreePopup.nameHeader]. */
private const val NAME_FIELD_SLACK = 3

/**
 * Sample string whose rendered width is reserved for the version column, so a resolved version fits without resize.
 * Wide enough for a `major.minor.patch` plus a pre-release suffix (e.g. `3.15.0rc1`, `3.15.0a0`).
 */
private const val VERSION_RESERVE_SAMPLE = "0.00.00rc0"


/** Gap between a section header's caption and the rule that runs on from it. */
private const val SEPARATOR_RULE_GAP = 6

/**
 * Space (unscaled px) above a section header, so the rows under it read as a block rather than as one running list.
 *
 * Only the caption-then-rule look takes it, and only below the top row. A header at the top of a popup has nothing to be
 * separated from, and a plain header brings the platform's own allowance for the full-width rule it draws above itself.
 */
private const val SEPARATOR_TOP_GAP = 6

/** Gap between the footer strip's caption and its chevron. */
private const val FOOTER_ICON_GAP = 2

/** The platform's own gap (unscaled px) between a row's text and its `>` arrow, restated so a row without one matches. */
private const val NEXT_STEP_INSET = 20

/** The same gap, tightened for a row that shows an icon of its own (reload, "…") right before the arrow. */
private const val NEXT_STEP_TIGHT_INSET = 4

/** Same footprint as the inline "…", for rows that can show it but are not hovered — see `customizeComponent`. */
private val MORE_ICON_PLACEHOLDER: Icon = EmptyIcon.create(AllIcons.Actions.More)

/**
 * Captions whose header carries the settings gear. Both spellings of the same section — it reads "Select" before an
 * interpreter is configured and "Change" afterwards — so the gear survives the switch.
 *
 * File-level rather than a property of [EvoTreePopup]: `GroupedElementsRenderer`'s constructor calls
 * `createSeparator()`, so the renderer reads these before its own `popup` field has been assigned. Anything reached
 * from there has to be independent of the popup instance.
 */
private val GEAR_CAPTIONS: Set<String> = setOf(
  PySdkFrontendBundle.message("evo.sdk.status.bar.popup.select.environment"),
  PySdkFrontendBundle.message("evo.sdk.status.bar.popup.change.environment"),
)

/**
 * Captions rendered as the platform's plain group header rather than this popup's caption-then-rule look.
 *
 * Both name the section that closes the popup below the tool list. Which of the two is there depends only on whether an
 * interpreter is set, so they are drawn the same way and the popup does not change shape when one replaces the other.
 */
private val PLAIN_CAPTIONS: Set<String> = setOf(
  PySdkFrontendBundle.message("evo.sdk.status.bar.popup.current.environment"),
  PySdkFrontendBundle.message("evo.sdk.status.bar.popup.shortcuts"),
)

/**
 * A group-header separator that also paints a settings gear at its right edge — but only while it is rendering one of
 * the [gearCaptions] sections (the "Select/Change Environment" header). A painted separator can't hold real action
 * components, so the gear click is hit-tested and handled in [EvoTreePopup].
 *
 * [plainCaptions] opt back out of this class's custom look entirely, rendering as the platform's own group header — a
 * full-width rule with the caption below it — for headers that read better as an ordinary divider.
 */
private class GearGroupHeaderSeparator(
  private val labelInsets: Insets,
  private val gearCaptions: Set<String>,
  private val plainCaptions: Set<String>,
  /** Read fresh on every paint, so hover state never has to be pushed into this reused component. */
  private val isGearHovered: () -> Boolean,
) : GroupHeaderSeparator(labelInsets) {
  /** True while this reused component is currently rendering the gear-bearing section. */
  val showsGear: Boolean get() = caption in gearCaptions

  /** True while rendering a section that wants the platform's plain group header instead of this class's look. */
  private val isPlain: Boolean get() = caption in plainCaptions

  /** Space held open above this header — see [SEPARATOR_TOP_GAP]. Zero at the top of a list, where [isHideLine] is set. */
  private val topGap: Int get() = if (caption == null || isHideLine) 0 else JBUI.scale(SEPARATOR_TOP_GAP)


  /**
   * The caption's own height, without the allowance [GroupHeaderSeparator] makes for the rule it draws above the text.
   *
   * That rule is a full-width one on its own line, which `PopupListElementRenderer` turns on for every separator but the
   * first — a line of height per section. This one runs the rule *beside* the caption instead (see [paintComponent]), so
   * the allowance is dropped here rather than left as blank space above every header.
   */
  override fun getPreferredElementSize(): Dimension {
    if (isPlain) return super.getPreferredElementSize()   // includes the platform's allowance for the rule above
    val size = if (caption == null) Dimension(0, 0) else getLabelSize(labelInsets)
    // Ask for the gear's own footprint as well. Without it the popup is sized to the caption alone, and a caption that
    // just fits leaves the gear — painted over the same line — sitting on its last word.
    if (showsGear) size.width += gearFootprint()
    size.height += topGap
    JBInsets.addTo(size, insets)
    return size
  }

  /** Width the gear needs at the right end of its header: the icon, its right inset, and the gap before it. */
  private fun gearFootprint(): Int = gearIcon().iconWidth + JBUI.scale(GEAR_RIGHT_INSET + GEAR_CAPTION_GAP)

  /**
   * Paints the caption and, filling the rest of the line, the rule — `Python 3.14 ───────`.
   *
   * Done here rather than by [GroupHeaderSeparator], whose rule sits above the caption on a line of its own: the two
   * cannot be combined by configuration, only replaced. The caption itself is laid out exactly as the superclass lays it
   * out, so a header too long for the popup is still clipped with an ellipsis rather than painted past the edge.
   */
  override fun paintComponent(g: Graphics) {
    if (isPlain) return super.paintComponent(g)
    val caption = caption ?: return
    val bounds = Rectangle(width, height)
    JBInsets.removeFrom(bounds, insets)
    bounds.y += topGap
    bounds.height -= topGap
    bounds.x += labelInsets.left
    bounds.width -= labelInsets.left + labelInsets.right
    bounds.y += labelInsets.top
    bounds.height -= labelInsets.top + labelInsets.bottom
    // The gear occupies the right end of this very line, so take that end away before the caption is laid out in what
    // is left. Otherwise the caption is laid out across the whole line and only ellipsizes at the popup's edge, which
    // let a long caption run under the gear.
    if (showsGear) {
      val room = gearBounds().x - JBUI.scale(GEAR_CAPTION_GAP) - bounds.x
      bounds.width = max(0, min(bounds.width, room))
    }

    val metrics = g.fontMetrics
    val iconR = Rectangle()
    val textR = Rectangle()
    val label = SwingUtilities.layoutCompoundLabel(
      metrics, caption, null, SwingConstants.CENTER, SwingConstants.LEFT, SwingConstants.CENTER, SwingConstants.LEFT,
      bounds, iconR, textR, 0)
    UISettings.setupAntialiasing(g)
    g.color = textForeground
    g.drawString(label, textR.x, textR.y + metrics.ascent)

    // The gear takes the right end of its own header, so that one gets no rule — the two would overlap.
    if (showsGear) {
      val icon = gearIcon()
      val at = gearBounds()
      // Hovered, it gets the same rounded fill an action button does, so it reads as something clickable.
      if (isGearHovered()) {
        val pad = JBUI.scale(GEAR_HOVER_PADDING)
        val arc = JBUI.scale(GEAR_HOVER_ARC)
        g.color = JBUI.CurrentTheme.ActionButton.hoverBackground()
        RectanglePainter.FILL.paint(
          g as Graphics2D, at.x - pad, at.y - pad, at.width + 2 * pad, at.height + 2 * pad, arc)
      }
      icon.paintIcon(this, g, at.x, at.y)
      return
    }
    // Only when the caption was not clipped: a rule after an ellipsis would suggest there is room left on the line.
    if (label != caption) return
    val from = textR.x + textR.width + JBUI.scale(SEPARATOR_RULE_GAP)
    val to = bounds.x + bounds.width
    if (to <= from) return
    // On the caption's strikethrough line, which is where the platform puts a rule it draws beside text.
    val y = textR.y + metrics.ascent + metrics.getLineMetrics(label, g).strikethroughOffset.toInt()
    g.color = foreground
    g.fillRect(from, y, to - from, 1)
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

  /**
   * The clickable area: [gearBounds] grown by the hover fill's padding, so the button's whole painted surface responds
   * rather than only the glyph inside it.
   */
  fun gearHitBounds(): Rectangle {
    val pad = JBUI.scale(GEAR_HOVER_PADDING)
    return Rectangle(gearBounds()).also { JBInsets.addTo(it, JBUI.insets(pad)) }
  }
}

class EvoPopupListElementRenderer(private val popup: EvoTreePopup) : PopupListElementRenderer<EvoTreeItem>(popup) {
  private val reloadLabel = JLabel()
  private val moreLabel = JLabel()

  // Replace the plain group header with one that paints a settings gear on the "Select Environment" section.
  @Suppress("DEPRECATION") // overrides a platform method that returns the deprecated SeparatorWithText
  override fun createSeparator(): SeparatorWithText {
    val labelInsets = if (ExperimentalUI.isNewUI()) JBUI.CurrentTheme.Popup.separatorLabelInsets()
                      else defaultItemComponentBorder.getBorderInsets(JLabel())
    // Runs from the superclass constructor, so it must not touch `popup` — only the lambda may, and that is invoked
    // at paint time, by which point the field is assigned.
    return GearGroupHeaderSeparator(labelInsets, GEAR_CAPTIONS, PLAIN_CAPTIONS) { popup.gearHovered }
  }

  /**
   * Paints the gear-bearing row as unselected while the pointer is on the gear.
   *
   * The section header — gear included — is painted *inside* its first row's cell, so hovering the gear puts the
   * pointer genuinely within that row and the platform selects it. Pointing at a header control should not look like
   * pointing at the environment below it, so the selection is suppressed here, in the painting only: clearing the real
   * selection would break the gear's own click, since `ListPopupImpl.MyList.processMouseEvent` forwards a click to
   * listeners only while the row under the pointer is the selected one.
   */
  override fun getListCellRendererComponent(
    list: JList<out EvoTreeItem>?,
    value: EvoTreeItem?,
    index: Int,
    isSelected: Boolean,
    cellHasFocus: Boolean,
  ): Component {
    val onGearHeader = value?.separatorAbove?.text?.let { it in GEAR_CAPTIONS } == true
    val paintSelected = isSelected && !(onGearHeader && popup.gearHovered)
    return super.getListCellRendererComponent(list, value, index, paintSelected, cellHasFocus)
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
    val canShowMore = popup.hasAlternatives(value)
    // Written on every row, not only where it is tightened: the renderer reuses one label for the whole list, so a
    // border left by the previous row would otherwise follow the arrow down it.
    arrow.border = when {
      // An icon of the row's own sits right before the arrow, so the platform's wide gap is tightened to hold both.
      showReload || canShowMore -> JBUI.Borders.emptyLeft(NEXT_STEP_TIGHT_INSET)
      ExperimentalUI.isNewUI() -> JBUI.Borders.emptyLeft(NEXT_STEP_INSET)
      else -> JBUI.Borders.empty()
    }
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
    }
    if (canShowMore) {
      // The width is reserved on every row that *can* show the icon, and the icon is only painted on the hovered one.
      // Adding the label on hover instead would take that width from the row's label, clipping the version text — the
      // same reason the version column below is reserved rather than sized when its value arrives.
      moreLabel.icon = if (isSelected) AllIcons.Actions.More else MORE_ICON_PLACEHOLDER
      buttonPane.add(moreLabel, gb.next().weightx(0.0))
    }
    // Every expandable row keeps the platform's standard ">" arrow, even though this popup's submenus always open to the
    // LEFT (see EvoTreePopup.show) — the arrow reads as "has a submenu" rather than as a direction, and matching the
    // platform look everywhere beats being literal about which side it appears on.
    buttonPane.add(arrow, gb.next().weightx(0.0))
    // Only a row that shows a version has something to line up, so only that row pays the width for the arrow it lacks.
    if (!arrow.isVisible && value?.reservesVersionColumn == true) {
      buttonPane.add(Box.createHorizontalStrut(nextStepReserve(arrow)), gb.next().weightx(0.0))
    }
  }

  /**
   * Width to hold open where a row has no `>` arrow, so its version column ends where every other row's does.
   *
   * The platform hides the arrow on a row with no submenu, and gives that row the inline-button separator's width back
   * as right inset — an allowance it drops again as soon as there is an arrow. Both differences are undone here, since
   * whether a row can be expanded says nothing about where its version should be read.
   */
  private fun nextStepReserve(arrow: JLabel): Int {
    val insets = arrow.insets
    val width = AllIcons.Icons.Ide.MenuArrow.iconWidth + insets.left + insets.right
    return max(0, if (ExperimentalUI.isNewUI()) width - myButtonSeparator.preferredSize.width else width)
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

  /**
   * True for the popup the widget itself opens, and false for every submenu under it.
   *
   * A getter rather than a stored value, so it holds while the superclass constructor still runs. [WizardPopup] assigns
   * its parent there, and [setShowSubmenuOnHover] can be called from that far up.
   */
  private val isTopLevel: Boolean get() = parent == null

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
   * Keeps hover expansion at the top level. A node inside a submenu expands on click.
   *
   * The value is refused here rather than only left unset at construction, because [ListPopupImpl] pushes its own
   * setting onto every child it creates, and it does so after [createPopup] has returned. Without this, hover expansion
   * would reach the whole tree, and a pointer crossing the rows of one submenu on its way elsewhere would unfold one
   * level after another.
   */
  override fun setShowSubmenuOnHover(showSubmenuOnHover: Boolean) {
    super.setShowSubmenuOnHover(showSubmenuOnHover && isTopLevel)
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

  /**
   * Settles this popup once a tool node's rows replace its "Loading…" row (see [EvoTreeMessageLeafElement]).
   *
   * The platform only grows the popup from its current position, which for a submenu of this widget means growing over
   * the parent it opened to the left of, and it leaves the selection alone. Neither is right for rows that arrive after
   * the popup was shown: a message row cannot be selected, so nothing is selected while a node loads.
   */
  override fun onModelChanged() {
    super.onModelChanged()
    selectFirstSelectableRow()
    repositionLeftOfParent()
  }

  /** Selects the first row the user can act on, when the selection is empty. */
  private fun selectFirstSelectableRow() {
    if (list.selectedIndex >= 0) return
    val step = listStep
    for (row in 0 until list.model.size) {
      if (step.isSelectable(list.model.getElementAt(row))) {
        list.selectedIndex = row
        break
      }
    }
  }

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
    val header = evoStep?.editableName?.let { nameHeader(it) }
                 ?: evoStep?.fixedName?.let { staticNameHeader(it) }
    val footer = expandCollapseFooter()
    if (header == null && footer == null) return content
    trimBodyInsetsCoveredBy(header, footer)
    return JPanel(BorderLayout()).apply {
      // This wrapper is what shows through behind the header and the footer (both non-opaque), so it has to carry the
      // popup's own background — a default JPanel one paints those bands a different shade than the rows.
      background = JBUI.CurrentTheme.Popup.BACKGROUND
      header?.let { add(it, BorderLayout.NORTH) }
      add(content, BorderLayout.CENTER)
      footer?.let { add(it, BorderLayout.SOUTH) }
    }
  }

  /**
   * Drops the padding the platform put above the first row and below the last, on whichever side this popup covers with
   * a band of its own.
   *
   * `PopupUtil.getListInsets` pads the list body for a popup that has *neither* a title header *nor* an ad strip: the top
   * inset stands in for the missing header, the bottom one for the missing ad. This popup builds both itself — the
   * name-field header and the expand/collapse footer — so where it does, that inset is no longer breathing room against
   * the popup's edge but a gap between the band and the rows.
   *
   * The insets are read back off the list rather than recomputed: the platform picks them from the theme (and differs
   * between the old and new UI), and only the sides actually covered are zeroed, leaving the rest as the theme set them.
   */
  private fun trimBodyInsetsCoveredBy(header: JComponent?, footer: JComponent?) {
    val insets = (list.border as? EmptyBorder)?.borderInsets ?: return
    list.border = JBUI.Borders.empty(
      if (header != null) 0 else insets.top,
      insets.left,
      if (footer != null) 0 else insets.bottom,
      insets.right,
    )
  }

  /**
   * The expand/collapse toggle under an add-new version list, as muted text: collapsed shows one row per Python version,
   * expanded turns each version into a header and lists that version's actual installs beneath it.
   *
   * Null — no toggle at all — when the machine has no version with a second install, since expanding would then only
   * put a header above each row it already shows. Called from the [WizardPopup] constructor, so it reads the step (set)
   * and never this class's own fields (not yet initialized); the listener body runs later and may.
   */
  private fun expandCollapseFooter(): JComponent? {
    val versionRows = evoStep?.versionRows?.takeIf { it.canExpand } ?: return null
    val expanded = versionRows.isExpanded
    // The caption names the action, not the state: it is set once, because clicking it replaces this whole popup.
    val caption = PySdkFrontendBundle.message(
      if (expanded) "evo.sdk.status.bar.popup.add.new.collapse"
      else "evo.sdk.status.bar.popup.add.new.expand"
    )
    // The platform's own strip-under-a-popup component, background and all. The band is what makes this read as a
    // control rather than as a caption, so the whole band is the click target — not just the text on it.
    return HintUtil.createAdComponent(caption, footerBorder(), SwingConstants.RIGHT).apply {
      // Which way the rows are about to move: down to reveal the interpreters, up to fold them away again.
      icon = if (expanded) AllIcons.General.ChevronUp else AllIcons.General.ChevronDown
      horizontalTextPosition = SwingConstants.LEADING   // chevron after the text, nearest the edge it sits against
      iconTextGap = JBUI.scale(FOOTER_ICON_GAP)
      cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
      addMouseListener(object : MouseAdapter() {
        override fun mouseClicked(e: MouseEvent) = toggleExpandedAndReopen()
      })
    }
  }

  /** The footer strip's border: the platform's ad-strip insets, plus a separating line above it on the old UI. */
  private fun footerBorder(): Border? =
    if (ExperimentalUI.isNewUI()) JBUI.CurrentTheme.Advertiser.border()
    else JBUI.Borders.compound(
      JBUI.Borders.customLineTop(JBUI.CurrentTheme.Advertiser.borderColor()),
      JBUI.CurrentTheme.Advertiser.border(),
    )

  /**
   * Expands or collapses the version list and rebuilds this submenu from scratch, rather than swapping the rows
   * underneath it.
   *
   * A popup is laid out and placed once, around the list it was built with. Replacing that list in place leaves it
   * sized for the view it no longer shows, and positioned for a width it no longer has — these submenus are anchored by
   * their RIGHT edge (see [show]), so any width change has to move them. Reopening runs the whole path again — a fresh
   * step over the new sections, then [show] and [afterShow] doing the placement — which is the only way the contents,
   * the size and the position all end up agreeing.
   */
  private fun toggleExpandedAndReopen() {
    // Read before the toggle: this reports what the user asked for, not the state left behind.
    CommonDataKeys.PROJECT.getData(dataContext)?.let { project ->
      val expanding = evoStep?.versionRows?.isExpanded == false
      PyEvoWidgetCollector.controlUsed(
        project,
        if (expanding) PyEvoWidgetCollector.Control.EXPAND else PyEvoWidgetCollector.Control.COLLAPSE,
      )
    }
    val step = evoStep ?: return
    val parentPopup = parent as? EvoTreePopup ?: return
    step.toggleExpanded()
    // Deferred: this runs from the footer's own mouse handler, inside the popup that is about to be disposed. Asking
    // the parent to re-choose its selected row is what reopens the submenu — that row is the one that opened this.
    SwingUtilities.invokeLater {
      if (parentPopup.isDisposed) return@invokeLater
      parentPopup.disposeChildren()
      parentPopup.handleSelect(false, null)
    }
  }

  /**
   * The env-name field an add-new submenu shows above its rows. See [createContent] for how it is stacked.
   */
  private fun nameHeader(editable: EvoEditableName): JComponent {
    // Sized to its own text and left-aligned, so the name follows the caption directly and the pencil — an extension at
    // the field's right edge — lands right after it: the header then reads as one phrase naming both halves of the step,
    // rather than a label and a value at opposite ends of the line.
    val field = nameField(editable.value)
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
        // The field is content-sized, so the row has to be re-laid out for it to follow the text — and with it the
        // pencil beside it. Revalidated on the *parent*: JTextField.isValidateRoot() is true outside a viewport, so
        // revalidating the field itself only ever re-lays out its interior, leaving its bounds — and the pencil — put.
        field.parent?.let { row ->
          row.revalidate()
          row.repaint()
        }
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
      else {
        // Scroll back to the head of the name. The field is laid out at its built width first and only then widened to
        // its content, and the view keeps whatever offset it took to hold the caret at the end — so a name that fits
        // perfectly can still be painted with its first characters cut off, which reads as a different name.
        field.caretPosition = 0
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
    // A muted caption opening the header line, which the name field completes: "Choose Base Python for .venv2".
    //
    // It names the rows below rather than the step ("New Environment") because the step was never the ambiguous half —
    // a bare "Python 3.12" sitting under a name like `.venv2` was, most of all in the expanded view where the rows are
    // interpreter paths and the version has moved into the section header.
    return captionRow(field)
  }

  /**
   * The header of an add-new submenu whose name is fixed — hatch's declared environment, named in `pyproject.toml` and
   * not ours to rename. Reads the same as the editable one, minus the pencil.
   */
  private fun staticNameHeader(name: @NlsSafe String): JComponent = captionRow(
    // The same component as the editable header's, only read-only. A JBLabel would be the obvious choice and the wrong
    // one: it carries none of a text field's insets, so it sits tight against the caption and makes the header shorter —
    // the two headers would be different shapes. Non-opaque and non-editable, this paints as plain text.
    nameField(name).apply {
      isEditable = false
      isFocusable = false
      caretPosition = 0
    }
  )

  /**
   * The name component of either header: content-sized, so the caption runs straight into it, and capped at
   * [NAME_FIELD_COLUMNS] so a long name claims no more of the popup than the header reserved for it.
   *
   * The cap matters because this header is rebuilt whenever the popup is — the expand/collapse toggle reopens it, see
   * [toggleExpandedAndReopen] — and an uncapped name would size the rebuilt popup to itself. Past the cap the name
   * scrolls inside the field, as it did when the field was a fixed width.
   */
  private fun nameField(name: @NlsSafe String): ExtendableTextField =
    object : ExtendableTextField(name) {
      override fun getPreferredSize(): Dimension {
        val preferred = super.getPreferredSize()
        // The measured text width plus a little: at exactly its own width the field still clips a character's overhang,
        // and the caret occupies the same area, so a name that fits reads as one that does not.
        val wanted = preferred.width + JBUI.scale(NAME_FIELD_SLACK)
        val cap = columnWidth * NAME_FIELD_COLUMNS + insets.left + insets.right
        return Dimension(min(wanted, cap), preferred.height)
      }

      // Pinned to the width above, so the row's layout can neither stretch the field past its cap nor stretch it
      // vertically to the row's height.
      override fun getMaximumSize(): Dimension = preferredSize
    }.apply {
      isOpaque = false
      border = JBUI.Borders.empty(1, 0)
      horizontalAlignment = SwingConstants.LEADING
      columns = 0
    }

  /**
   * The header line both name headers share: the caption, then [name], as one phrase.
   *
   * Packed left rather than caption-and-value at opposite ends, and laid out by GridBagLayout for its BASELINE anchor —
   * the caption is a plain label and an editable name a text field with insets of its own, so the two are different
   * heights, and any layout that centres them vertically (BoxLayout, FlowLayout) sits the name off the caption's
   * baseline. Neither does GridBag wrap, so a name outgrowing the room left on the line stays on it rather than dropping
   * to a second row. The trailing glue takes the slack, keeping the two packed at the left.
   */
  private fun captionRow(name: JComponent): JComponent {
    val caption = JBLabel(PySdkFrontendBundle.message("evo.sdk.status.bar.popup.add.new.base.caption")).apply {
      foreground = NamedColorUtil.getInactiveTextColor()
      border = JBUI.Borders.emptyLeft(8)
    }
    return object : JPanel(GridBagLayout()) {
      // An editable name is a content-sized field that grows as a longer name is typed, and the popup cannot widen once
      // it is open — the header would just be clipped mid-edit. So the room to type is reserved here instead: the header
      // is never narrower than the caption plus [NAME_FIELD_COLUMNS] of text, whatever the name currently is. A fixed
      // name reserves the same, which is what keeps one tool's submenu from opening narrower than the next one's.
      override fun getPreferredSize(): Dimension {
        val natural = super.getPreferredSize()
        val columnWidth = name.getFontMetrics(name.font).charWidth('m')
        val reserved = caption.preferredSize.width + JBUI.scale(NAME_FIELD_GAP) + columnWidth * NAME_FIELD_COLUMNS
        return Dimension(max(natural.width, reserved), natural.height)
      }
    }.apply {
      isOpaque = false
      val gb = GridBag()
        .setDefaultAnchor(GridBagConstraints.BASELINE)
        .setDefaultFill(GridBagConstraints.NONE)
        .setDefaultWeightY(0.0)
      add(caption, gb.nextLine().next())
      // GridBag.insets scales what it is given, so the raw value goes in here — unlike the reserve computed above,
      // which is plain pixel arithmetic and scales its own.
      add(name, gb.next().insets(0, NAME_FIELD_GAP, 0, 0))
      // The glue has no baseline to align to, so it is anchored on its own rather than joining the row's baseline group.
      add(Box.createHorizontalGlue(), gb.next().weightx(1.0).anchor(GridBagConstraints.CENTER))
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

  /** Tooltip shown while hovering the settings gear. */
  private val gearTooltip: @NlsContexts.Tooltip String = PySdkFrontendBundle.message("evo.sdk.status.bar.popup.settings.gear.tooltip")

  /** Tooltip shown while hovering a row's inline "…" — what the finer choice behind it is. */
  private val moreTooltip: @NlsContexts.Tooltip String = PySdkFrontendBundle.message("evo.sdk.status.bar.popup.base.python.tooltip")

  init {
    setMaxRowCount(maxRowCount)
    // A tool node expands on hover, and only in the popup the widget opens — see [setShowSubmenuOnHover].
    isShowSubmenuOnHover = isTopLevel
  }

  override fun afterShow() {
    super.afterShow()
    // Re-assert the hand cursor `ListPopupImpl.createList` already set, which looks redundant and is not: this popup can
    // appear under a stationary pointer (the expand/collapse toggle replaces it in place), and AWT only re-evaluates the
    // cursor when the pointer crosses a component boundary. The list is a single component, so moving between rows never
    // crosses one and a cursor left over from the popup that was just disposed would stay for as long as the pointer
    // remains inside. Component.setCursor updates the native cursor outright, which is what breaks that.
    list.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    // Resolve lazy detail (e.g. interpreter version) for the rows currently in the viewport, and again for any
    // rows scrolled into view later — never for the whole list up front. See [EvoLazyDetail].
    (list.parent as? JViewport)?.addChangeListener { resolveVisibleDetails() }
    resolveVisibleDetails()

    // A click on a tool's inline reload icon re-scans that tool; a click on the "Select Environment" gear opens the
    // Package Managers settings. isActionClick() below stops either from also selecting/expanding a row.
    list.addMouseListener(object : MouseAdapter() {
      override fun mouseExited(e: MouseEvent) = setGearHovered(false)

      override fun mouseReleased(e: MouseEvent) {
        val more = moreIconItemAt(e.point)
        if (more != null) {
          showAlternatives(more)
          return
        }
        reloadIconItemAt(e.point)?.let { item ->
          CommonDataKeys.PROJECT.getData(dataContext)?.let { project ->
            PyEvoWidgetCollector.controlUsed(project, PyEvoWidgetCollector.Control.RELOAD, item.evoNodeStats() ?: EvoNodeStats(EvoNodeKind.OTHER))
          }
          evoStep?.reloadItem(item)
        }
        if (settingsGearAt(e.point)) openPackageManagersSettings()
      }
    })

    // Two hover targets live in the list's own tooltip: the gear's help text, and the full folder path behind an elided
    // section header. They share one listener because each would otherwise clear the other's tooltip on the next move.
    // The remembered values guard against redundant per-move updates.
    // The cursor is deliberately not touched here. `ListPopupImpl.createList` already gives the whole list a hand
    // cursor, which is right for every part of it — the rows, the inline icons and the gear are all click targets — and
    // setting it per-position only ever downgraded the rows to a plain arrow.
    list.addMouseMotionListener(object : MouseMotionAdapter() {
      private var shownTooltip: @Nls String? = null
      override fun mouseMoved(e: MouseEvent) {
        val overGear = settingsGearAt(e.point)
        setGearHovered(overGear)
        // Pointing at the header's gear is not pointing at the row it is painted over, so that row's submenu should
        // not stay open behind it. Done on every move rather than only on entry: the platform's hover timer can still
        // be pending from the row itself and would otherwise reopen the submenu under the pointer.
        if (overGear) disposeChildren()
        val overMore = !overGear && moreIconItemAt(e.point) != null
        val tooltip = when {
          overGear -> gearTooltip
          overMore -> moreTooltip
          // The header strip is painted inside the top cell of its section, so it is checked before the row under it.
          else -> separatorTooltipAt(e.point)
        }
        if (tooltip != shownTooltip) {
          shownTooltip = tooltip
          list.setToolTipText(tooltip?.let { HtmlChunk.raw(multiLineTooltip(it)) })
        }
      }
    })

    // Selecting another row closes whatever the previous row had opened, so at most one submenu is ever open below this
    // popup. The platform does this only while it expands on hover, and this popup does that at the top level alone —
    // see [setShowSubmenuOnHover]. It reads the selection rather than the pointer, so it also covers the keyboard, and
    // so it inherits the platform's rule that travelling towards an open submenu does not change the selection.
    list.addListSelectionListener(object : ListSelectionListener {
      /**
       * The selection this last acted on.
       *
       * The list reports one selection more than once, and a repeat must do nothing. It would close the submenu that
       * the very same row had just opened.
       */
      private var lastIndex: Int = -1

      override fun valueChanged(e: ListSelectionEvent) {
        // The platform copies every listener of this list onto each child popup it creates, so this same object also
        // hears a child's selection. Only the list this popup owns may close this popup's child.
        if (e.source !== list || e.valueIsAdjusting) return
        val index = list.selectedIndex
        if (index == lastIndex) return
        lastIndex = index
        disposeChildren()
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

  // Don't let a click on one of the inline icons or the settings gear select/expand a row — the mouse listener handles
  // all three.
  override fun isActionClick(e: MouseEvent): Boolean =
    reloadIconItemAt(e.point) == null && moreIconItemAt(e.point) == null &&
    !settingsGearAt(e.point) && super.isActionClick(e)


  /** True when [item]'s row should carry the inline "…" that opens its finer choices. */
  fun hasAlternatives(item: EvoTreeItem?): Boolean = item?.alternatives != null

  /**
   * Opens [item]'s finer choices (the installs behind an add-new Python version) as a child popup — its inline "…" was
   * clicked. Anchored on that row, which the click already selected.
   */
  private fun showAlternatives(item: EvoTreeItem) {
    val step = evoStep ?: return
    val alternatives = item.alternatives ?: return
    CommonDataKeys.PROJECT.getData(dataContext)?.let { project ->
      PyEvoWidgetCollector.controlUsed(project, PyEvoWidgetCollector.Control.ALTERNATIVES, item.evoNodeStats() ?: EvoNodeStats(EvoNodeKind.OTHER))
    }
    if (myChild != null) return   // already open for this row
    handleNextStep(step.alternativesStep(alternatives), item, null)
  }

  /** The tool [EvoTreeItem] whose inline reload icon (only shown on the hovered row) contains [point], or null. */
  private fun reloadIconItemAt(point: Point): EvoTreeItem? =
    inlineIconItemAt(point, AllIcons.Actions.Refresh) { evoStep?.isReloadable(it) == true }

  /** The [EvoTreeItem] whose inline "…" (only shown on the hovered row) contains [point], or null. */
  private fun moreIconItemAt(point: Point): EvoTreeItem? =
    inlineIconItemAt(point, AllIcons.Actions.More) { hasAlternatives(it) }

  /**
   * The item at [point] whose row carries [icon] under the pointer, or null. [applies] is the row's own precondition for
   * having the icon at all — checked before the cell is laid out, which is the expensive part.
   *
   * Only the hovered row answers, since both inline icons are painted only there. That also means the cell can be laid
   * out as selected, which is how it is actually painted — an icon located in an unselected cell would land elsewhere.
   */
  private fun inlineIconItemAt(point: Point, icon: Icon, applies: (EvoTreeItem) -> Boolean): EvoTreeItem? {
    val row = list.locationToIndex(point)
    if (row < 0 || row != list.selectedIndex) return null
    val item = list.model.getElementAt(row) as? EvoTreeItem ?: return null
    if (!applies(item)) return null
    val bounds = inlineIconBounds(row, item, icon) ?: return null
    return if (bounds.contains(point)) item else null
  }

  /**
   * Bounds (in list coordinates) of [icon] in [row], found by laying out the row's rendered cell as selected — which is
   * the only state these icons are painted in, and so the only layout their position can be read from.
   */
  private fun inlineIconBounds(row: Int, item: EvoTreeItem, icon: Icon): Rectangle? {
    val cell = list.getCellBounds(row, row) ?: return null
    @Suppress("UNCHECKED_CAST")
    val jList = list as JList<Any?>
    val renderer = jList.cellRenderer ?: return null
    val comp = renderer.getListCellRendererComponent(jList, item, row, true, true) as? JComponent ?: return null
    comp.setBounds(0, 0, cell.width, cell.height)
    layoutRecursively(comp)
    val label = findLabelWithIcon(comp, icon) ?: return null
    val topLeft = SwingUtilities.convertPoint(label, 0, 0, comp)
    return Rectangle(cell.x + topLeft.x, cell.y + topLeft.y, label.width, label.height)
  }

  private fun layoutRecursively(c: Component) {
    c.doLayout()
    if (c is Container) c.components.forEach { layoutRecursively(it) }
  }

  /** The rendered row's label showing exactly [icon] — identity, since these are the shared AllIcons instances. */
  private fun findLabelWithIcon(c: Component, icon: Icon): JLabel? {
    if (c is JLabel && c.icon === icon) return c
    if (c is Container) c.components.forEach { child -> findLabelWithIcon(child, icon)?.let { return it } }
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
    return item.separatorAbove?.text in GEAR_CAPTIONS && settingsGearBounds(row, item)?.contains(point) == true
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
    val gb = sep.gearHitBounds()
    val topLeft = SwingUtilities.convertPoint(sep, gb.x, gb.y, comp)
    return Rectangle(cell.x + topLeft.x, cell.y + topLeft.y, gb.width, gb.height)
  }

  /** True while the pointer is over the settings gear; the separator reads this when it paints. */
  internal var gearHovered: Boolean = false
    private set

  /** Records gear hover and repaints, but only when the state actually flips — this runs on every mouse move. */
  private fun setGearHovered(hovered: Boolean) {
    if (gearHovered == hovered) return
    gearHovered = hovered
    list.repaint()
  }

  private fun findGearSeparator(c: Component): GearGroupHeaderSeparator? {
    if (c is GearGroupHeaderSeparator) return c
    if (c is Container) c.components.forEach { child -> findGearSeparator(child)?.let { return it } }
    return null
  }

  private fun openPackageManagersSettings() {
    val project = CommonDataKeys.PROJECT.getData(dataContext) ?: return
    PyEvoWidgetCollector.controlUsed(project, PyEvoWidgetCollector.Control.GEAR_SETTINGS)
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


