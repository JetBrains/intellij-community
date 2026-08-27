package com.intellij.python.processOutput.frontend.ui.components

import com.intellij.icons.AllIcons
import com.intellij.openapi.util.IconLoader
import com.intellij.python.processOutput.common.ProcessWeightDto
import com.intellij.python.processOutput.frontend.ProcessTreeNode
import com.intellij.python.processOutput.frontend.TreeFilter
import com.intellij.python.processOutput.frontend.icons.PythonProcessOutputFrontendIcons
import com.intellij.python.processOutput.frontend.ui.ProcessOutputUiContext
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.icons.IconWithOverlay
import com.intellij.util.IconUtil
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Shape
import java.awt.geom.Ellipse2D
import javax.swing.Icon
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.tree.TreeCellRenderer
import javax.swing.tree.TreeNode

internal class ProcessTreeCellRenderer(private val uiContext: ProcessOutputUiContext) : TreeCellRenderer {
  private val component = JPanel()
  private val title = JBLabel()
  private val eastLabel = JBLabel()

  init {
    title.iconTextGap = JBUI.scale(Styling.ICON_TEXT_GAP)
    title.isOpaque = false
    title.minimumSize = Dimension(0, title.minimumSize.height)

    eastLabel.foreground = Styling.TIME_COLOR
    eastLabel.iconTextGap = JBUI.scale(Styling.ICON_TEXT_GAP)

    val eastPanel = JPanel()

    eastPanel.isOpaque = false
    eastPanel.add(eastLabel)

    component.layout = BorderLayout()
    component.border = JBUI.Borders.emptyRight(Styling.NODE_EAST_OFFSET)
    component.add(title, BorderLayout.CENTER)
    component.add(eastPanel, BorderLayout.EAST)
  }

  override fun getTreeCellRendererComponent(
    tree: JTree?,
    value: Any?,
    selected: Boolean,
    expanded: Boolean,
    leaf: Boolean,
    row: Int,
    hasFocus: Boolean,
  ): Component {
    val node = value as ProcessTreeNode

    updateTitle(node)
    updateEastLabel(node)

    return component
  }

  private fun updateEastLabel(treeNode: ProcessTreeNode) {
    val activeFilters = uiContext.controller.treeSectionState.filters

    eastLabel.text =
      if (activeFilters[TreeFilter.Item.SHOW_TIME]) {
        treeNode.formattedTimestamp
      }
      else {
        null
      }

    eastLabel.icon =
      if (activeFilters[TreeFilter.Item.SHOW_PROCESS_WEIGHT]) {
        when (treeNode) {
          is ProcessTreeNode.Context -> null
          is ProcessTreeNode.Process ->
            when (treeNode.weight) {
              ProcessWeightDto.MEDIUM -> Styling.MEDIUM_PROCESS_ICON
              ProcessWeightDto.HEAVY -> Styling.HEAVY_PROCESS_ICON
              ProcessWeightDto.LIGHT, null -> null
            }
        }
      }
      else {
        null
      }
  }

  private fun updateTitle(treeNode: ProcessTreeNode) {
    title.text = treeNode.title
    title.icon = processIcon(treeNode)
    title.foreground =
      when (treeNode) {
        is ProcessTreeNode.Context -> null
        is ProcessTreeNode.Process ->
          if (treeNode.isCriticalError) {
            Styling.CRITICAL_ERROR_COLOR
          }
          else {
            null
          }

      }
  }

  private fun processIcon(treeNode: ProcessTreeNode): Icon =
    when (treeNode) {
      is ProcessTreeNode.Context ->
        Styling.CONTEXT_ICON
      is ProcessTreeNode.Process -> {
        when {
          treeNode.isRunning ->
            AnimatedIcon.Default.INSTANCE
          treeNode.isCriticalError ->
            Styling.CRITICAL_ERROR_ICON
          else -> {
            val icon = treeNode.processIcon?.icon ?: Styling.PROCESS_ICON

            icon
              .let {
                if (treeNode.isError) {
                  errorIcon(it)
                }
                else {
                  it
                }
              }
              .let {
                if (treeNode.isBackground) {
                  IconLoader.getTransparentIcon(it, Styling.BACKGROUND_PROCESS_ALPHA)
                }
                else {
                  it
                }
              }
          }
        }
      }
    }

  private fun errorIcon(parentIcon: Icon) =
    object : IconWithOverlay(parentIcon, scaledErrorSubicon(parentIcon)) {
      override fun getOverlayShape(x: Int, y: Int): Shape {
        val radius = Styling.ERROR_CUTOUT_RADIUS
        val centerX = parentIcon.iconWidth.toFloat() - Styling.ERROR_CUTOUT_OFFSET
        val centerY = parentIcon.iconHeight.toFloat() - Styling.ERROR_CUTOUT_OFFSET
        return Ellipse2D.Float(centerX - radius, centerY - radius, radius * 2f, radius * 2f)
      }
    }

  private fun scaledErrorSubicon(parentIcon: Icon) =
    object : Icon {
      private val scaledSubicon =
        IconUtil.scale(
          Styling.ERROR_ICON,
          null,
          Styling.ERROR_ICON_SIZE.toFloat() / Styling.ERROR_ICON.iconWidth.toFloat(),
        )

      override fun paintIcon(component: Component?, graphics: Graphics?, x: Int, y: Int) {
        val dx = parentIcon.iconWidth - scaledSubicon.iconWidth + Styling.ERROR_ICON_OFFSET
        val dy = parentIcon.iconHeight - scaledSubicon.iconHeight + Styling.ERROR_ICON_OFFSET
        scaledSubicon.paintIcon(component, graphics, x + dx, y + dy)
      }

      override fun getIconWidth(): Int =
        parentIcon.iconWidth + Styling.ERROR_ICON_OFFSET

      override fun getIconHeight(): Int =
        parentIcon.iconHeight + Styling.ERROR_ICON_OFFSET
    }

  private object Styling {
    const val NODE_EAST_OFFSET = 16
    const val ERROR_ICON_SIZE = 10
    const val ERROR_ICON_OFFSET = 2
    const val ERROR_CUTOUT_OFFSET = 3
    const val ERROR_CUTOUT_RADIUS = 6.5f
    const val BACKGROUND_PROCESS_ALPHA = 0.5f
    const val ICON_TEXT_GAP = 6
    val PROCESS_ICON = PythonProcessOutputFrontendIcons.Process
    val CONTEXT_ICON = AllIcons.Nodes.Folder
    val ERROR_ICON = AllIcons.General.Error
    val MEDIUM_PROCESS_ICON = PythonProcessOutputFrontendIcons.ProcessMedium
    val HEAVY_PROCESS_ICON = PythonProcessOutputFrontendIcons.ProcessHeavy
    val CRITICAL_ERROR_ICON = PythonProcessOutputFrontendIcons.ResultIncorrect
    val TIME_COLOR = JBColor.namedColor("Component.infoForeground")
    val CRITICAL_ERROR_COLOR = JBColor.namedColor("Label.errorForeground")
  }
}

internal val TreeNode.processTreeNode: ProcessTreeNode
  get() =
    this as ProcessTreeNode
