package com.intellij.python.processOutput.frontend.ui.components

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.python.processOutput.frontend.LoggedProcess
import com.intellij.python.processOutput.frontend.ProcessOutputBundle.message
import com.intellij.python.processOutput.frontend.ProcessStatus
import com.intellij.python.processOutput.frontend.formatCompact
import com.intellij.python.processOutput.frontend.ui.ProcessOutputUiContext
import com.intellij.ui.ColorUtil
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.SideBorder
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.AsyncProcessIcon
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.Box
import javax.swing.JComponent
import javax.swing.JPanel

internal class OutputSection(private val uiContext: ProcessOutputUiContext) {
  private val statusPrefixLabel: JBLabel = JBLabel()
  private val statusValueLabel: JBLabel = JBLabel()
  private val spinner: AsyncProcessIcon = AsyncProcessIcon(uiContext.coroutineScope)
  private val timePrefixLabel: JBLabel = JBLabel()
  private val timeValueLabel: JBLabel = JBLabel()
  private val pidPrefixLabel: JBLabel = JBLabel()
  private val pidValueLabel: JBLabel = JBLabel()

  private val spinnerHolder: JComponent = Box.createHorizontalBox()
  private val statusSegment: JComponent = Box.createHorizontalBox()
  private val timeSegment: JComponent = Box.createHorizontalBox()
  private val pidSegment: JComponent = Box.createHorizontalBox()

  val component: JComponent
    field = JPanel(BorderLayout())

  init {
    statusPrefixLabel.minimumSize = Dimension(0, statusPrefixLabel.minimumSize.height)
    spinner.suspend()

    statusPrefixLabel.foreground = Styling.MUTED_LABEL_COLOR

    statusSegment.add(statusPrefixLabel)
    statusSegment.add(Box.createHorizontalStrut(Styling.PREFIX_VALUE_GAP))
    statusSegment.add(statusValueLabel)

    spinnerHolder.add(Box.createHorizontalStrut(Styling.STATUS_SPINNER_GAP))
    spinnerHolder.add(spinner)
    spinnerHolder.isVisible = false

    timePrefixLabel.foreground = Styling.MUTED_LABEL_COLOR

    timeSegment.add(Box.createHorizontalStrut(Styling.TITLE_SEGMENT_GAP))
    timeSegment.add(timePrefixLabel)
    timeSegment.add(Box.createHorizontalStrut(Styling.PREFIX_VALUE_GAP))
    timeSegment.add(timeValueLabel)
    timeSegment.isVisible = false

    pidPrefixLabel.foreground = Styling.MUTED_LABEL_COLOR

    pidSegment.add(Box.createHorizontalStrut(Styling.TITLE_SEGMENT_GAP))
    pidSegment.add(pidPrefixLabel)
    pidSegment.add(Box.createHorizontalStrut(Styling.PREFIX_VALUE_GAP))
    pidSegment.add(pidValueLabel)
    pidSegment.isVisible = false

    uiContext.coroutineScope.launch(Dispatchers.EDT) {
      var statusJob: Job? = null

      uiContext.controller.selectedProcess.collect { process ->
        statusJob?.cancelAndJoin()
        statusJob = null

        if (process == null) {
          renderTitle(null, null)
        }
        else {
          statusJob =
            this@launch.launch(Dispatchers.EDT) {
              process.status.collect { status ->
                renderTitle(process, status)
              }
            }
        }
      }
    }

    component.add(toolbar(), BorderLayout.NORTH)
    component.add(Console(uiContext).component, BorderLayout.CENTER)
  }

  private fun renderTitle(process: LoggedProcess?, status: ProcessStatus?) {
    if (process == null || status == null) {
      statusPrefixLabel.text = ""
      statusValueLabel.text = ""
      spinner.suspend()
      spinnerHolder.isVisible = false
      timeSegment.isVisible = false
      pidSegment.isVisible = false

      return
    }

    when (status) {
      ProcessStatus.Running -> {
        setPrefixAndValue(
          statusPrefixLabel,
          statusValueLabel,
          message("process.output.output.header.status.running"),
        )
        spinnerHolder.isVisible = true
        spinner.resume()
        timeSegment.isVisible = false
      }
      is ProcessStatus.Done -> {
        val exitCodeString = status.exitCode.toString()
        val (text, color) =
          if (status.exitCode == 0) {
            message("process.output.output.header.status.ok", exitCodeString) to Styling.SUCCESS_FOREGROUND
          }
          else {
            message("process.output.output.header.status.error", exitCodeString) to Styling.ERROR_FOREGROUND
          }
        val colored = "<font color='#${ColorUtil.toHex(color)}'>$text</font>"
        val elapsed = (status.exitedAt - process.data.startedAt).formatCompact()

        setPrefixAndValue(
          statusPrefixLabel,
          statusValueLabel,
          message("process.output.output.header.status.done", colored),
          valueContainsHtml = true,
        )

        spinner.suspend()
        spinnerHolder.isVisible = false

        setPrefixAndValue(
          timePrefixLabel,
          timeValueLabel,
          message("process.output.output.header.time", elapsed),
        )
        timeSegment.isVisible = true
      }
    }

    val pid = process.data.pid

    if (pid != null) {
      setPrefixAndValue(
        pidPrefixLabel,
        pidValueLabel,
        message("process.output.output.header.pid", pid.toString()),
      )
      pidSegment.isVisible = true
    }
    else {
      pidSegment.isVisible = false
    }
  }

  @Suppress("HardCodedStringLiteral")
  private fun setPrefixAndValue(
    prefixLabel: JBLabel,
    valueLabel: JBLabel,
    @Nls message: String,
    valueContainsHtml: Boolean = false,
  ) {
    val colonIndex = message.indexOf(':')

    if (colonIndex < 0) {
      prefixLabel.text = message
      valueLabel.text = ""
      return
    }

    val prefix = message.substring(0, colonIndex + 1)
    val value = message.substring(colonIndex + 1).trimStart()

    prefixLabel.text = prefix
    valueLabel.text = if (valueContainsHtml) "<html>$value</html>" else value
  }

  private fun toolbar(): JPanel {
    val panel = JPanel(BorderLayout())

    panel.border = IdeBorderFactory.createBorder(SideBorder.BOTTOM)

    val row = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0))

    row.isOpaque = false
    row.add(statusSegment)
    row.add(spinnerHolder)
    row.add(timeSegment)
    row.add(pidSegment)

    val titleWrapper = JPanel(GridBagLayout())

    titleWrapper.isOpaque = false
    titleWrapper.border = JBUI.Borders.empty(0, Styling.TITLE_HORIZONTAL_PADDING)
    titleWrapper.add(
      row,
      GridBagConstraints().apply {
        gridx = 0
        gridy = 0
        anchor = GridBagConstraints.WEST
        weightx = 1.0
        fill = GridBagConstraints.NONE
      },
    )

    panel.add(titleWrapper, BorderLayout.CENTER)
    panel.add(actionToolbar().component, BorderLayout.EAST)

    return panel
  }

  private fun actionToolbar(): ActionToolbar {
    val actionToolbar =
      ActionManager
        .getInstance()
        .createActionToolbar(
          ActionPlaces.TOOLWINDOW_CONTENT,
          actionGroup(),
          true,
        )

    actionToolbar.targetComponent = uiContext.rootPanel

    return actionToolbar
  }

  private fun actionGroup(): DefaultActionGroup {
    val group = DefaultActionGroup()

    group.add(
      filterActionGroup(
        name = message("process.output.output.buttons.displayOptions"),
        state = uiContext.controller.outputSectionState.filters,
        onFilterItemToggled = { filterItem, enabled ->
          uiContext.controller.onOutputFilterItemToggled(filterItem, enabled)
        }
      )
    )

    group.add(
      object : DumbAwareAction(message("process.output.output.buttons.copyOutput")) {
        init {
          templatePresentation.icon = AllIcons.General.Copy
        }

        override fun update(e: AnActionEvent) {
          e.presentation.isEnabled = uiContext.controller.selectedProcess.value != null
        }

        override fun getActionUpdateThread(): ActionUpdateThread =
          ActionUpdateThread.BGT

        override fun actionPerformed(e: AnActionEvent) {
          uiContext.controller.selectedProcess.value?.also {
            uiContext.controller.copyOutputToClipboard(it)
          }
        }
      }
    )

    return group
  }

  private object Styling {
    const val TITLE_HORIZONTAL_PADDING = 8
    const val TITLE_SEGMENT_GAP = 16
    const val STATUS_SPINNER_GAP = 4
    const val PREFIX_VALUE_GAP = 4
    val ERROR_FOREGROUND: Color = UIUtil.getErrorForeground()
    val SUCCESS_FOREGROUND: Color = UIUtil.getLabelSuccessForeground()
    val MUTED_LABEL_COLOR: Color = ColorUtil.withAlpha(JBUI.CurrentTheme.Label.foreground(), 0.75)
  }
}
