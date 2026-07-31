  // Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.toolwindow.packages.tree.renderers

import com.intellij.icons.AllIcons
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.hover.TreeHoverListener
import com.intellij.ui.paint.RectanglePainter
import com.intellij.ui.tree.ui.DefaultTreeUI
import com.intellij.util.IconUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.jetbrains.python.PyBundle
import com.jetbrains.python.icons.PythonIcons
import com.jetbrains.python.packaging.toolwindow.model.DependencyGroupNode
import com.jetbrains.python.packaging.toolwindow.model.DisplayablePackage
import com.jetbrains.python.packaging.toolwindow.PyPackageIcons
import com.jetbrains.python.packaging.toolwindow.model.InstallablePackage
import com.jetbrains.python.packaging.toolwindow.model.InstalledPackage
import com.jetbrains.python.packaging.toolwindow.model.LoadingNode
import com.jetbrains.python.packaging.toolwindow.model.RequirementPackage
import com.jetbrains.python.packaging.toolwindow.model.UndeclaredPackagesGroup
import com.jetbrains.python.packaging.toolwindow.model.WorkspaceMember
import com.jetbrains.python.packaging.toolwindow.packages.tree.PyPackagesTree
import java.awt.Graphics
import java.net.URI
import java.nio.file.Path
import javax.swing.Icon
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode

private fun URI.toDisplayString(): String = if (scheme == "file") Path.of(this).toString() else toString()

internal class PyPackageTreeCellRenderer(
  private val packagesTree: PyPackagesTree,
) : ColoredTreeCellRenderer() {
  var linkStartX: Int = -1
    private set
  var linkEndX: Int = -1
    private set
  private var currentRow: Int = -1

  /** Trailing action icon (delete or install) painted inline after the row content. */
  var trailingIcon: Icon? = null
    private set
  var trailingIconX: Int = -1
    private set

  /** Inline change-version icon painted right after the version text. */
  var inlineChangeVersionIcon: Icon? = null
    private set
  var inlineChangeVersionIconX: Int = -1
    private set

  companion object {
    private val WARNING_COLOR = JBUI.CurrentTheme.Label.warningForeground()
    private val LINK_COLOR = JBUI.CurrentTheme.Link.Foreground.ENABLED
    private val VERSION_ATTRIBUTES = SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, UIUtil.getInactiveTextColor())

    val INSTALL_ICON: Icon = PyPackageIcons.AddPackage
    val DELETE_ICON: Icon = PyPackageIcons.Uninstall
    val CHANGE_VERSION_ICON: Icon = IconUtil.colorize(AllIcons.Ide.Notification.PluginUpdate, UIUtil.getLabelForeground())
    val CHANGE_VERSION_ICON_BLUE: Icon = IconUtil.colorize(AllIcons.Ide.Notification.PluginUpdate, LINK_COLOR)
  }

  override fun customizeCellRenderer(
    tree: JTree, value: Any?, selected: Boolean, expanded: Boolean,
    leaf: Boolean, row: Int, hasFocus: Boolean,
  ) {
    linkStartX = -1
    linkEndX = -1
    currentRow = row
    trailingIcon = null
    trailingIconX = -1
    inlineChangeVersionIcon = null
    inlineChangeVersionIconX = -1
    toolTipText = null
    val node = value as DefaultMutableTreeNode
    val pkg = node.userObject as DisplayablePackage

    val hoveredRow = TreeHoverListener.getHoveredRow(packagesTree)
    val isRowHovered = hoveredRow == row || packagesTree.linkHoveredRow == row

    when (pkg) {
      is UndeclaredPackagesGroup -> renderUndeclaredGroup(pkg)
      is DependencyGroupNode -> renderDependencyGroup(pkg)
      is WorkspaceMember -> renderWorkspaceMember(pkg, expanded, node)
      is InstalledPackage -> renderInstalledPackage(pkg, node, isRowHovered)
      is RequirementPackage -> renderRequirementPackage(pkg, node)
      is InstallablePackage -> renderInstallablePackage(pkg, node.level, isRowHovered)
      is LoadingNode -> renderLoadingNode()
    }
  }

  override fun paintComponent(g: Graphics) {
    super.paintComponent(g)

    val inlineChange = inlineChangeVersionIcon
    if (inlineChange != null && inlineChangeVersionIconX > 0) {
      if (packagesTree.changeIconHoveredRow == currentRow) {
        paintIconHoverBackground(g, inlineChangeVersionIconX, inlineChange.iconWidth)
      }
      val iconY = (height - inlineChange.iconHeight) / 2
      inlineChange.paintIcon(this, g, inlineChangeVersionIconX, iconY)
    }

    val trailing = trailingIcon
    if (trailing != null && trailingIconX > 0) {
      if (packagesTree.iconHoveredRow == currentRow) {
        paintIconHoverBackground(g, trailingIconX, trailing.iconWidth)
      }
      val iconY = (height - trailing.iconHeight) / 2
      trailing.paintIcon(this, g, trailingIconX, iconY)
    }
  }

  private fun paintIconHoverBackground(g: Graphics, iconX: Int, iconWidth: Int) {
    val pad = JBUI.scale(2)
    val arc = JBUI.scale(4)
    val g2 = g.create() as java.awt.Graphics2D
    try {
      g2.color = JBUI.CurrentTheme.ActionButton.hoverBackground()
      RectanglePainter.FILL.paint(g2, iconX - pad, pad, iconWidth + pad * 2, height - pad * 2, arc)
    }
    finally {
      g2.dispose()
    }
  }

  private fun isAncestorOnlyMatch(pkg: DisplayablePackage): Boolean {
    val query = packagesTree.project.let {
      com.jetbrains.python.packaging.toolwindow.PyPackagingToolWindowService.getInstance(it).activeSearchQuery
    }
    if (query.isEmpty()) return false
    return !pkg.name.contains(query, ignoreCase = true)
  }

  private fun greyAttributes(base: SimpleTextAttributes): SimpleTextAttributes =
    SimpleTextAttributes(base.style, UIUtil.getInactiveTextColor())

  private fun renderUndeclaredGroup(pkg: UndeclaredPackagesGroup) {
    icon = AllIcons.General.Warning
    val warningAttributes = SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, WARNING_COLOR)
    append(pkg.name, warningAttributes)
  }

  private fun renderDependencyGroup(pkg: DependencyGroupNode) {
    icon = AllIcons.Toolwindows.Dependencies
    val attrs = if (isAncestorOnlyMatch(pkg)) VERSION_ATTRIBUTES else SimpleTextAttributes.REGULAR_ATTRIBUTES
    append(pkg.name, attrs)
  }

  private fun renderWorkspaceMember(pkg: WorkspaceMember, expanded: Boolean, node: DefaultMutableTreeNode) {
    icon = PythonIcons.Python.PythonClosed
    append(pkg.name)
    if (!expanded && node.childCount > 0) {
      append(" ${node.childCount}", VERSION_ATTRIBUTES)
    }
  }

  private fun renderInstalledPackage(pkg: InstalledPackage, node: DefaultMutableTreeNode, showActions: Boolean) {
    val isUndeclaredChild = PyPackageTreePresenter.isChildOfUndeclaredGroup(node.displayablePackageAncestors())
    val instance = pkg.instance
    val providerIcon = PyPackageInstalledIconProvider.EP_NAME.extensionList.firstNotNullOfOrNull { it.iconFor(instance) }

    icon = when {
      providerIcon != null -> providerIcon
      !pkg.isDeclared || isUndeclaredChild -> PyPackageIcons.PackagePipInstalled
      else -> PyPackageIcons.Package
    }

    val location = pkg.instance.editableLocation
    val isLocalInstall = location != null
    val italic = pkg.isEditMode || isLocalInstall
    val baseStyle = if (italic) SimpleTextAttributes.STYLE_ITALIC else SimpleTextAttributes.STYLE_PLAIN
    val baseAttributes = if (!pkg.isDeclared || isUndeclaredChild) {
      SimpleTextAttributes(baseStyle, WARNING_COLOR)
    }
    else if (italic) {
      SimpleTextAttributes(SimpleTextAttributes.STYLE_ITALIC, null)
    }
    else {
      SimpleTextAttributes.REGULAR_ATTRIBUTES
    }
    val nameAttributes = if (isAncestorOnlyMatch(pkg)) greyAttributes(baseAttributes) else baseAttributes
    append(pkg.name, nameAttributes)

    @NlsSafe val version = pkg.instance.version
    if (version.isNotEmpty()) {
      append(" $version", VERSION_ATTRIBUTES)
    }

    if (isLocalInstall) {
      val displayLocation = location.toDisplayString()
      toolTipText = if (pkg.isEditMode) {
        "<html>" + PyBundle.message("python.toolwindow.packages.editable.installed.from.tooltip", displayLocation) + "</html>"
      }
      else {
        PyBundle.message("python.toolwindow.packages.installed.from", displayLocation)
      }
    }
    else if (pkg.isEditMode) {
      toolTipText = PyBundle.message("python.toolwindow.packages.editable.package")
    }

    if (packagesTree.isReadOnly) return

    val gap = JBUI.scale(6)
    inlineChangeVersionIconX = preferredSize.width + gap
    inlineChangeVersionIcon = chooseInlineChangeVersionIcon(
      isLocalInstall = isLocalInstall,
      hasUpdate = pkg.nextVersion != null && pkg.canBeUpdated,
      showActions = showActions,
      updateAvailableIcon = CHANGE_VERSION_ICON_BLUE,
      defaultActionIcon = CHANGE_VERSION_ICON,
    )
    appendTextPadding(inlineChangeVersionIconX + CHANGE_VERSION_ICON.iconWidth + gap)

    val changeIconEnd = inlineChangeVersionIconX + CHANGE_VERSION_ICON.iconWidth
    val minPadTo = changeIconEnd - textLeftOffset() + JBUI.scale(8)
    val rowIndent = node.level.coerceAtLeast(0) * totalChildIndent()
    val viewportPadTo = packagesTree.visibleRect.width - packagesTree.insets.let { it.left + it.right } -
                        rowIndent - cellChromeWidth() - DELETE_ICON.iconWidth - JBUI.scale(DefaultTreeUI.HORIZONTAL_SELECTION_OFFSET)
    val padTo = maxOf(viewportPadTo, minPadTo)
    appendTextPadding(padTo)
    trailingIconX = textLeftOffset() + padTo
    trailingIcon = if (showActions) DELETE_ICON else null
    appendTextPadding(padTo + DELETE_ICON.iconWidth)
  }

  private fun renderRequirementPackage(pkg: RequirementPackage, node: DefaultMutableTreeNode) {
    val isUndeclaredChild = PyPackageTreePresenter.isChildOfUndeclaredGroup(node.displayablePackageAncestors())
    val providerIcon = PyPackageInstalledIconProvider.EP_NAME.extensionList.firstNotNullOfOrNull { it.iconFor(pkg.instance) }

    icon = when {
      providerIcon != null -> providerIcon
      !pkg.isDeclared || isUndeclaredChild -> PyPackageIcons.PackagePipInstalled
      else -> PyPackageIcons.Package
    }

    val baseAttributes = if (!pkg.isDeclared || isUndeclaredChild) {
      SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, WARNING_COLOR)
    }
    else {
      SimpleTextAttributes.REGULAR_ATTRIBUTES
    }
    val nameAttributes = if (isAncestorOnlyMatch(pkg)) greyAttributes(baseAttributes) else baseAttributes
    append(pkg.name, nameAttributes)

    @NlsSafe val version = pkg.instance.version
    if (version.isNotEmpty()) {
      append(" $version", VERSION_ATTRIBUTES)
    }
  }

  private fun renderInstallablePackage(pkg: InstallablePackage, depth: Int, showActions: Boolean) {
    icon = PyPackageIcons.PackageGray
    append(pkg.name)

    if (packagesTree.isReadOnly) return

    val gap = JBUI.scale(2)
    val linkText = PyBundle.message("action.python.packages.install.text")
    val textWidth = getFontMetrics(font).stringWidth(linkText)
    val iconWidth = INSTALL_ICON.iconWidth
    val blockWidth = textWidth + gap + iconWidth

    val padTo = rightAlignPadding(depth, blockWidth, blockWidth)
    appendTextPadding(padTo)
    val leftOff = textLeftOffset()

    if (showActions) {
      val linkStyle = if (packagesTree.linkHoveredRow == currentRow) SimpleTextAttributes.STYLE_UNDERLINE else SimpleTextAttributes.STYLE_PLAIN
      val linkAttributes = SimpleTextAttributes(linkStyle, LINK_COLOR)
      linkStartX = padTo
      append(linkText, linkAttributes)
      linkEndX = linkStartX + textWidth
      appendTextPadding(padTo + textWidth + gap)
      trailingIconX = leftOff + padTo + textWidth + gap
      trailingIcon = INSTALL_ICON
      appendTextPadding(padTo + blockWidth)
    }
    else {
      trailingIconX = leftOff + padTo + textWidth + gap
      appendTextPadding(padTo + blockWidth)
    }
  }

  private fun rightAlignPadding(depth: Int, blockWidth: Int, iconWidth: Int): Int {
    val treeWidth = packagesTree.visibleRect.width - packagesTree.insets.let { it.left + it.right }
    val rowIndent = depth.coerceAtLeast(0) * totalChildIndent()
    val availableWidth = treeWidth - rowIndent - cellChromeWidth()
    val rightInset = JBUI.scale(DefaultTreeUI.HORIZONTAL_SELECTION_OFFSET)
    return (availableWidth - blockWidth - rightInset).coerceAtLeast(preferredSize.width)
  }

  /** Total per-level horizontal indent (left + right) from the platform tree look & feel. */
  private fun totalChildIndent(): Int =
    UIUtil.getTreeLeftChildIndent() + UIUtil.getTreeRightChildIndent()

  /** Non-text width contributed by the renderer's leading icon, ipad and insets. */
  private fun cellChromeWidth(): Int {
    var w = ipad.left + ipad.right
    val leadIcon = icon
    if (leadIcon != null) {
      w += leadIcon.iconWidth + iconTextGap
    }
    val componentInsets = insets
    if (componentInsets != null) {
      w += componentInsets.left + componentInsets.right
    }
    return w
  }

  /** Component-coord X at which the first text fragment is painted (left chrome offset). */
  private fun textLeftOffset(): Int {
    var x = ipad.left
    val leadIcon = icon
    if (leadIcon != null) {
      x += leadIcon.iconWidth + iconTextGap
    }
    val componentInsets = insets
    if (componentInsets != null) {
      x += componentInsets.left
    }
    return x
  }

  private fun renderLoadingNode() {
    icon = AnimatedIcon.Default.INSTANCE
    append(
      PyBundle.message("python.toolwindow.packages.description.panel.loading"),
      SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, UIUtil.getInactiveTextColor())
    )
  }

}

/**
 * Chooses which inline icon (if any) to paint to the right of the package version.
 *
 * Extracted from the renderer so the precedence rules can be unit-tested without a Swing tree:
 * an editable / local install hides the icon entirely, an outstanding update wins over the
 * neutral row-hover icon, and the neutral icon is only shown while the row is hovered.
 */
internal fun chooseInlineChangeVersionIcon(
  isLocalInstall: Boolean,
  hasUpdate: Boolean,
  showActions: Boolean,
  updateAvailableIcon: Icon,
  defaultActionIcon: Icon,
): Icon? = when {
  isLocalInstall -> null
  hasUpdate -> updateAvailableIcon
  showActions -> defaultActionIcon
  else -> null
}

