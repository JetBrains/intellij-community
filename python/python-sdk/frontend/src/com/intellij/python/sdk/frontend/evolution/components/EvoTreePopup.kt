@file:Suppress("UnstableApiUsage")

package com.intellij.python.sdk.frontend.evolution.components

import com.intellij.ui.ComponentUtil
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
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.GroupHeaderSeparator
import com.intellij.ui.ScreenUtil
import com.intellij.ui.SeparatorWithText
import com.intellij.ui.components.JBLabel
import com.intellij.ui.paint.RectanglePainter
import com.intellij.ui.popup.WizardPopup
import com.intellij.ui.popup.list.ListPopupImpl
import com.intellij.ui.popup.list.PopupListElementRenderer
import com.intellij.util.IconUtil
import com.intellij.util.ui.GridBag
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Container
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
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
import javax.swing.Timer
import javax.swing.JPanel
import javax.swing.JViewport
import javax.swing.ListCellRenderer
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import javax.swing.border.Border
import javax.swing.border.EmptyBorder
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

/** Left inset shared by the header's caption and the sync box under it, so the two line up as one block. */
private const val CAPTION_LEFT_INSET = 8

/**
 * Vertical padding for a caption standing on its own as a panel title.
 *
 * A caption beside a name needs none: the name is a text field, and its insets set the header's height for both. Alone,
 * the caption is a bare label with no insets of its own — and the platform's own top padding has been given up to make
 * room for this header (`EvoTreePopup.trimBodyInsetsCoveredBy`) — so without it the title sits against the popup's edge.
 */
private const val CAPTION_ONLY_VERTICAL_INSET = 5

/**
 * Width (unscaled px) the footer's description wraps at, and so the widest it can make a panel.
 *
 * A sentence is far wider than the rows it describes — "Python 3.15" needs a fifth of it — and a plain label reports its
 * whole length as its preferred width, which made every panel as wide as its longest line. Wrapping at a fixed width
 * puts the panel's size back in the hands of its rows.
 */
private const val FOOTER_TEXT_WIDTH = 240


/** The platform's own gap (unscaled px) between a row's text and its `>` arrow, restated so a row without one matches. */
private const val NEXT_STEP_INSET = 20

/** The same gap, tightened for a row that shows an inline reload icon of its own right before the arrow. */
private const val NEXT_STEP_TIGHT_INSET = 4

/**
 * The same gap for a row whose version column stands between its text and the arrow.
 *
 * The platform's wide gap keeps a row's label off the chevron. A version column does that already, and it is padded to
 * [VERSION_RESERVE_SAMPLE] besides, so the wide gap only pushes the arrow further out and the popup wider with it.
 */
private const val NEXT_STEP_VERSION_INSET = 8

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
 * [plainCaptions] — and a section headed by a rule and no caption — opt back out of this class's custom look entirely,
 * rendering as the platform's own group header: a full-width rule with the caption below it. See [isPlain].
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

  /**
   * True while rendering a section that wants the platform's plain group header instead of this class's look.
   *
   * A section headed by a rule and no caption is one of them, and this class's look cannot draw it: the rule is placed
   * beside the caption, and there is no caption to place it beside. The superclass paints its rule before it looks at
   * one, and sizes itself to that rule, which is the whole of such a header.
   *
   * [isVisible] is what finds those sections. `SeparatorWithText.getCaption` reports a blank caption as null, so the
   * `ListSeparator("")` above such a section is indistinguishable here from a row that has no separator at all — but the
   * renderer shows this component only where the model does say a separator belongs.
   */
  private val isPlain: Boolean get() = caption?.let { it in plainCaptions } ?: isVisible

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
  private val signLabel = JLabel()

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
      myTextLabel.isEnabled = value.isEnabled
      reserveVersionColumn(value)
    }
    // A row that reveals more of the list is drawn in the platform's link colour, so it reads as a control rather than
    // as an environment. Only when unselected: the selection foreground is what stays legible on the highlight, and the
    // superclass sets that on every row, so nothing has to be undone here.
    if (value?.isLinkRow == true && !isSelected) myTextLabel.foreground = JBUI.CurrentTheme.Link.Foreground.ENABLED

    // Put a reload icon right next to the submenu arrow for the hovered refreshable tool row (click handled in EvoTreePopup).
    val arrow = myNextStepLabel ?: return
    val buttonPane = arrow.parent as? JComponent ?: return
    val element = value?.element
    val showReload = isSelected && element is EvoTreeLazyNodeElement && element.refreshable && element.state == State.DONE
    // Written on every row, not only where it is tightened: the renderer reuses one label for the whole list, so a
    // border left by the previous row would otherwise follow the arrow down it.
    arrow.border = when {
      // An icon of the row's own sits right before the arrow, so the platform's wide gap is tightened to hold both.
      showReload -> JBUI.Borders.emptyLeft(NEXT_STEP_TIGHT_INSET)
      value?.reservesVersionColumn == true -> JBUI.Borders.emptyLeft(NEXT_STEP_VERSION_INSET)
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
    // Every expandable row keeps the platform's standard ">" arrow, even though this popup's submenus always open to the
    // LEFT (see EvoTreePopup.show) — the arrow reads as "has a submenu" rather than as a direction, and matching the
    // platform look everywhere beats being literal about which side it appears on.
    buttonPane.add(arrow, gb.next().weightx(0.0))
    val statusIcon = value?.statusIcon
    when {
      // A row that did not work says so where every other row keeps its arrow, so the signs of a list line up with each
      // other instead of trailing labels of every width. Painted whether or not the row is hovered: it is a state, not
      // something to act on.
      !arrow.isVisible && statusIcon != null -> {
        signLabel.icon = statusIcon
        buttonPane.add(signLabel, gb.next().weightx(0.0))
      }
      // A row that shows a version and no sign, in a list where something carries an arrow: it holds the same width
      // open so every version column in the list ends at the same place. Without this, a row nothing can be done with —
      // a hatch environment with no interpreter to build it — sat with its "n/a" further right than the rest.
      !arrow.isVisible && value?.reservesVersionColumn == true && popup.hasExpandableRow() ->
        buttonPane.add(Box.createHorizontalStrut(nextStepReserve(arrow)), gb.next().weightx(0.0))
    }
  }

  /**
   * Width to hold open where a row has no `>` arrow, so its version column ends where an expandable row's does.
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

  /**
   * Where this popup sits on screen, when it was placed at a point instead of beside its parent — the picker an inline
   * icon opens. Null for the widget's own popup and for every ordinary submenu.
   *
   * Such a popup is still a child step, which is what keeps its parent open behind it: a popup of its own would be a
   * separate window, and the parent cancels when its window is deactivated. Only the placement differs.
   */
  private var anchorOnScreen: Point? = null

  /** [anchorOnScreen] for the child this popup is about to open, set by [openBasePythonPanel] and read by [createPopup]. */
  private var nextChildAnchor: Point? = null


  override fun getListElementRenderer(): ListCellRenderer<*>? {
    return EvoPopupListElementRenderer(this)
  }

  // Keep sub-popups (expanded nodes) EvoTreePopup too, so the custom renderer, speed search and lazy version
  // resolution apply at every level — the platform would otherwise create a plain ListPopupImpl here.
  override fun createPopup(parent: WizardPopup?, step: PopupStep<*>?, parentValue: Any?): WizardPopup {
    if (step is EvoActionPopupStep) {
      // A panel opened over a tool that has not answered yet is opened again once it has, rather than grown in place.
      //
      // Growing it never worked: the platform sizes a popup into the space below its own top, this widget's panels open
      // low on the screen, and a panel two rows tall while it says "Loading…" has almost nothing under it to grow into.
      // Reopening builds the panel from the rows it now has — the path the second open always took, and the reason the
      // second open looked right.
      //
      // Registered here, on the popup that owns the row, and fired later by the node. Asking for this from inside the
      // panel's own model callback tore down the whole widget: that callback runs while the popup is being updated, and
      // disposing it from there cancels the chain it belongs to. [reloadShowingLoadingPanel] takes the same route.
      (step.node as? EvoTreeLazyNodeElement)
        ?.takeIf { it.state == State.LOADING }
        ?.let { node -> node.whenLoadFinished { if (node.state == State.DONE) openSubmenuOf(node) } }
      // Read before the child exists: inside `apply` the name would bind to the child's own field, which is null.
      val anchor = nextChildAnchor
      return EvoTreePopup(parent, step, null, dataContext, maxRowCount).apply {
        anchorOnScreen = anchor
        // [WizardPopup.show] moves any child that overlaps its parent clear of it, to the left. An anchored popup is
        // meant to overlap — it opens on the row it belongs to — so it opts out of that alignment and is placed where
        // it asked to be, fitted to the screen and nothing more.
        if (anchor != null) isAlignByParentBounds = false
      }
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
   * with a "<"): flip the requested x here, before the popup is ever painted — [reposition] alone would show
   * it on the right for one frame. [owner] is the parent popup's content component (see `ListPopupImpl.showNextStepPopup`).
   */
  override fun show(owner: Component, aScreenX: Int, aScreenY: Int, considerForcedXY: Boolean) {
    // An anchored popup is placed where the row that opens it is, overlapping the parent — see [anchorOnScreen].
    val target = anchorOnScreen
    if (target != null) {
      super.show(owner, target.x, target.y, considerForcedXY)
      return
    }
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
   * Repaints the list while any visible row carries a spinner, one tick per frame of it.
   *
   * Started in [afterShow] and stopped in [dispose], so it lives exactly as long as this popup. A tick over a list with
   * no spinner costs one scan of the rows on screen and paints nothing.
   */
  private val loaderRepaintTimer: Timer = Timer(AnimatedIcon.Default.DELAY) {
    if (showsAnyLoader()) list.repaint()
  }

  /** True when a row of this popup is drawing a spinner right now — see [EvoTreeItem.showsLoader]. */
  private fun showsAnyLoader(): Boolean {
    val model = list.model
    for (row in 0 until model.size) {
      if ((model.getElementAt(row) as? EvoTreeItem)?.showsLoader == true) return true
    }
    return false
  }

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
    reposition()
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

  /**
   * Wraps the list in this panel's own bands: the title line above it, and the step's description below.
   *
   * Called from the [WizardPopup] constructor, so it relies only on the step (already set), not on this class's own
   * fields (not yet initialized).
   */
  override fun createContent(): JComponent {
    val content = super.createContent()
    val header = evoStep?.headerCaption?.let { headerRow(it) }
    val footer = footer()
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
   * The strip along the bottom of an inner panel: what its rows do, and — where there is one — the toggle between the
   * two views of a version list.
   *
   * Every inner panel has the strip, even with no toggle to put in it, because the description is the point: a row says
   * which environment or which Python, never what choosing it does to the project. Null only for a panel that says
   * nothing and offers no toggle, which is the top-level list.
   *
   * Called from the [WizardPopup] constructor, so it reads the step (set) and never this class's own fields (not yet
   * initialized); the listener bodies run later and may.
   */
  private fun footer(): JComponent? {
    // One line, and text only. The versions toggle used to sit here too: beside the sentence it squeezed it, and under
    // it the band grew to two lines of padding for one short control. It reads as a heading of the list it switches, so
    // it now sits in the header — see [headerRow].
    val description = evoStep?.stepDescription ?: return null
    // Wrapped at a fixed width rather than left to its own length — see [FOOTER_TEXT_WIDTH]. A label wraps only when it
    // is given HTML, and the width has to be stated there for the wrap to happen at all.
    val wrapped = HtmlChunk.html().children(
      HtmlChunk.body().style("width:${JBUI.scale(FOOTER_TEXT_WIDTH)}px").addText(description)
    ).toString()
    // The platform's own strip-under-a-popup component, background and all.
    return HintUtil.createAdComponent(wrapped, footerBorder(), SwingConstants.LEFT)
  }

  /** The footer strip's border: the platform's ad-strip insets, plus a separating line above it on the old UI. */
  private fun footerBorder(): Border? =
    if (ExperimentalUI.isNewUI()) JBUI.CurrentTheme.Advertiser.border()
    else JBUI.Borders.compound(
      JBUI.Borders.customLineTop(JBUI.CurrentTheme.Advertiser.borderColor()),
      JBUI.CurrentTheme.Advertiser.border(),
    )

  /**
   * Reloads the tool of [item] and reports it in a panel of its own, then opens the real submenu once it ends.
   *
   * The reload leaves the node's rows and controls alone, so nothing has to be rebuilt from a half-replaced state: while
   * it runs, the submenu is an independent "Loading…" panel ([EvoActionPopupStep.loadingStep]), and when it ends the
   * node's own submenu is opened over its finished state — header, expand/collapse footer and all.
   *
   * A reload that failed opens nothing: the node is disabled and carries the sign, and the rows it had stay readable
   * behind it.
   */
  private fun reloadShowingLoadingPanel(item: EvoTreeItem) {
    val node = item.element as? EvoTreeLazyNodeElement ?: return
    val step = evoStep ?: return
    // Registered before the load starts, so a reload that ends quickly cannot end before anyone is listening.
    node.whenLoadFinished { openSubmenuOf(node) }
    step.reloadItem(item)
    openChildStep(step.loadingStep(), node)
  }

  /**
   * The row selected here, when it is still the one showing [element]; null when the user moved on.
   *
   * Addressed by element rather than by row: [EvoActionPopupStep.getValues] builds a fresh [EvoTreeItem] for every row
   * each time the model is rebuilt, and a load rebuilds it — so an item held across one is no longer the item in the
   * model, while the element it wraps lives in the tree and outlasts every popup.
   */
  private fun selectedItemFor(element: EvoTreeElement): EvoTreeItem? {
    val row = list.selectedIndex
    if (row < 0) return null
    val item = list.model.getElementAt(row) as? EvoTreeItem ?: return null
    return item.takeIf { it.element === element }
  }

  /**
   * Opens [element]'s own submenu, replacing whatever is open, once the event that asked for it has been dispatched.
   *
   * Only [element] is reopened, never simply "the selection": a slow reload gives the user time to move on, and the row
   * they moved to is not the one whose load finished.
   *
   * Deferred because this runs from the list's own mouse handler, or from a loader's callback, and disposing a child
   * popup underneath either is not safe.
   */
  private fun openSubmenuOf(element: EvoTreeElement) {
    SwingUtilities.invokeLater {
      if (isDisposed || selectedItemFor(element) == null) return@invokeLater
      disposeChildren()
      handleSelect(false, null)
    }
  }

  /**
   * Replaces any open child popup with one showing [step], anchored on [element]'s row.
   *
   * [anchor], in list coordinates, puts the child under that rectangle instead — what an icon opens is placed at the
   * icon, the way a context menu is, rather than along the far edge of the parent. See [anchorOnScreen].
   */
  private fun openChildStep(step: PopupStep<*>, element: EvoTreeElement, anchor: Rectangle? = null) {
    SwingUtilities.invokeLater {
      if (isDisposed) return@invokeLater
      val item = selectedItemFor(element) ?: return@invokeLater
      disposeChildren()
      nextChildAnchor = anchor?.let { below(it) }
      handleNextStep(step, item, null)
      nextChildAnchor = null
    }
  }

  /** The screen point just under the left edge of [rect], which is given in list coordinates. */
  private fun below(rect: Rectangle): Point =
    Point(rect.x, rect.y + rect.height).also { SwingUtilities.convertPointToScreen(it, list) }

  /**
   * The panel's own line: what this step is.
   */
  private fun headerRow(caption: @Nls String): JComponent = JBLabel(caption).apply {
    border = JBUI.Borders.empty(CAPTION_ONLY_VERTICAL_INSET, CAPTION_LEFT_INSET, CAPTION_ONLY_VERTICAL_INSET, 0)
  }

  /** Caption of the section header that carries the settings gear. */

  /** Tooltip shown while hovering the settings gear. */
  private val gearTooltip: @NlsContexts.Tooltip String = PySdkFrontendBundle.message("evo.sdk.status.bar.popup.settings.gear.tooltip")


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
    // An AnimatedIcon painted by a renderer advances only when the component it is painted on is repainted, and it asks
    // for that repaint itself only when it can find the list behind the renderer and that list allows it. This is the
    // property that allows it, and it is what every other list and tree with a spinner sets.
    list.putClientProperty(AnimatedIcon.ANIMATION_IN_RENDERER_ALLOWED, true)
    // It did not animate the loader here even so, and the icon asks for nothing when it cannot resolve that list — so
    // the repaint is driven from here as well rather than left to a lookup this popup evidently falls outside of. A tick
    // repaints only while a spinner is actually on screen, and the timer dies with the popup.
    loaderRepaintTimer.start()
    // Resolve lazy detail (e.g. interpreter version) for the rows currently in the viewport, and again for any
    // rows scrolled into view later — never for the whole list up front. See [EvoLazyDetail].
    //
    // The viewport is found by walking up, not by casting the list's own parent. [WizardPopup] scrolls whatever
    // [createContent] returns, and this popup returns the list wrapped in bands of its own — so the list's parent is
    // that wrapper, the cast gave nothing, and no row ever resolved but the ones on screen when the popup opened.
    ComponentUtil.getParentOfType(JViewport::class.java, list)?.addChangeListener { resolveVisibleDetails() }
    // Moving the selection scrolls too, and with the keyboard it is the only thing that does.
    list.addListSelectionListener { resolveVisibleDetails() }
    resolveVisibleDetails()

    // A click on a tool's inline reload icon re-scans that tool; a click on the "Select Environment" gear opens the
    // Package Managers settings. isActionClick() below stops either from also selecting/expanding a row.
    list.addMouseListener(object : MouseAdapter() {
      override fun mouseExited(e: MouseEvent) = setGearHovered(false)

      override fun mouseReleased(e: MouseEvent) {
        // The right button is what offers a row's base Pythons. No icon stands for it: an icon on every such row cost
        // the width of the whole trailing column, and the rows it appeared on are most of a tool's list.
        if (SwingUtilities.isRightMouseButton(e)) {
          openBasePythonPanel(e.point)
          return
        }
        reloadIconItemAt(e.point)?.let { item ->
          CommonDataKeys.PROJECT.getData(dataContext)?.let { project ->
            PyEvoWidgetCollector.controlUsed(project, PyEvoWidgetCollector.Control.RELOAD, item.evoNodeStats() ?: EvoNodeStats(EvoNodeKind.OTHER))
          }
          reloadShowingLoadingPanel(item)
          return
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
        val tooltip = when {
          overGear -> gearTooltip
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

    // [show] already placed this popup using its *preferred* width; correct it now that the laid-out width is known,
    // and clamp it into the screen (a submenu wider than the space on the left would otherwise hang off it).
    reposition()
  }

  /** Screen x for a submenu [width] px wide that sits entirely left of a parent whose own left edge is [ownerX]. */
  private fun leftOfX(ownerX: Int, width: Int): Int = ownerX - width - JBUI.scale(SUBMENU_LEFT_GAP)

  /**
   * Puts this popup back where it belongs, now that its laid-out size is known: at its anchor, or left of its parent.
   *
   * Called after the show and again whenever the rows change, because the platform grows a popup from wherever it
   * already is and neither placement survives that on its own.
   */
  private fun reposition() {
    val self = content
    if (!self.isShowing) return
    val anchor = anchorOnScreen
    val target: Rectangle
    val screen: Rectangle
    if (anchor != null) {
      target = Rectangle(anchor.x, anchor.y, self.width, self.height)
      screen = ScreenUtil.getScreenRectangle(anchor)
    }
    else {
      // A submenu of this widget sits to the LEFT of its parent, using its real (laid-out) width.
      val parentContent = parent?.content?.takeIf { it.isShowing } ?: return
      target = Rectangle(leftOfX(parentContent.locationOnScreen.x, self.width), self.locationOnScreen.y, self.width, self.height)
      screen = ScreenUtil.getScreenRectangle(parentContent.locationOnScreen)
    }
    // Cropped as well as moved: a list of many environments is taller than the screen, and no position fits it. Cropping
    // gives those rows a scroll bar instead of leaving them past the bottom edge.
    ScreenUtil.moveToFit(target, screen, null, true)
    setLocation(target.location)
  }

  /**
   * True when some row of this list opens a submenu.
   *
   * A row without one holds the arrow's width open so its version column ends where an expandable row's does — see
   * [EvoPopupListElementRenderer.nextStepReserve]. Where nothing in the list can be expanded there is nothing to line
   * up with, and every row would pay for an arrow the list never draws: most of the trailing space of an add-new panel,
   * whose rows are all leaves.
   *
   * Read off the list model rather than off the step, because the renderer asks this on every repaint and
   * `EvoActionPopupStep.getValues` rebuilds its row list on every call.
   */
  fun hasExpandableRow(): Boolean {
    val step = evoStep ?: return false
    val model = list.model
    return (0 until model.size).any { step.hasSubstep(model.getElementAt(it) as? EvoTreeItem) }
  }

  // Don't let a click on one of the inline icons or the settings gear select/expand a row — the mouse listener handles
  // all three.
  override fun isActionClick(e: MouseEvent): Boolean =
    reloadIconItemAt(e.point) == null && !settingsGearAt(e.point) && super.isActionClick(e)

  /**
   * Opens the base-Python picker of the row at [point] — the right button was released over it.
   *
   * A popup of its own rather than a swap of these rows: the list the user came for stays on screen behind it, and the
   * picker is plainly a short question about one row instead of somewhere they have travelled to. It opens at the
   * pointer, the way a context menu does.
   *
   * The row is selected first: the picker is a child step, and a child step is anchored on the row the list has
   * selected. Nothing is skipped when a child is already open — [openChildStep] disposes whatever is showing first.
   */
  private fun openBasePythonPanel(point: Point) {
    val step = evoStep ?: return
    val row = list.locationToIndex(point).takeIf { it >= 0 } ?: return
    val item = list.model.getElementAt(row) as? EvoTreeItem ?: return
    val panel = item.basePythonPanel ?: return
    list.selectedIndex = row
    CommonDataKeys.PROJECT.getData(dataContext)?.let { project ->
      PyEvoWidgetCollector.controlUsed(project, PyEvoWidgetCollector.Control.BASE_PYTHON_PANEL,
                                       item.evoNodeStats() ?: EvoNodeStats(EvoNodeKind.OTHER))
    }
    openChildStep(step.childStep(panel), item.element, Rectangle(point.x, point.y, 0, 0))
  }

  /**
   * The tool row whose inline reload icon is under [point], or null.
   *
   * Only the selected row answers, since the icon is painted only there. That also means the cell can be laid out as
   * selected, which is how it is actually painted — an icon located in an unselected cell would land elsewhere. Whether
   * the row can be reloaded at all is checked before that layout, which is the expensive part.
   */
  private fun reloadIconItemAt(point: Point): EvoTreeItem? {
    val row = list.locationToIndex(point)
    if (row < 0 || row != list.selectedIndex) return null
    val item = list.model.getElementAt(row) as? EvoTreeItem ?: return null
    if (evoStep?.isReloadable(item) != true) return null
    val bounds = inlineIconBounds(row, item) ?: return null
    return if (bounds.contains(point)) item else null
  }

  /**
   * Bounds (in list coordinates) of the reload icon in [row], found by laying out the row's rendered cell as selected —
   * the only state the icon is painted in, and so the only layout its position can be read from.
   */
  private fun inlineIconBounds(row: Int, item: EvoTreeItem): Rectangle? {
    val icon = AllIcons.Actions.Refresh
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
    // The gear is painted inside the top row's cell, so that row answers for the tooltip and its own text would show
    // over the gear — see [EvoActionPopupStep.getTooltipTextFor]. Silencing the row is the only way the gear's own text
    // reaches the screen.
    evoStep?.rowTooltipSuppressed = hovered
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
    loaderRepaintTimer.stop()
    myDisposeCallback?.run()
    ActionMenu.showDescriptionInStatusBar(true, myComponent, null)
    super.dispose()
  }

}


