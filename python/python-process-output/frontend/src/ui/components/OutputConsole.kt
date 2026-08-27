package com.intellij.python.processOutput.frontend.ui.components

import com.intellij.openapi.application.EDT
import com.intellij.python.processOutput.common.LoggedProcessDto
import com.intellij.python.processOutput.common.OutputKindDto
import com.intellij.python.processOutput.common.OutputLineDto
import com.intellij.python.processOutput.frontend.InfoTag
import com.intellij.python.processOutput.frontend.OutputFilter
import com.intellij.python.processOutput.frontend.OutputTag
import com.intellij.python.processOutput.frontend.ProcessOutputBundle.message
import com.intellij.python.processOutput.frontend.ProcessOutputController
import com.intellij.python.processOutput.frontend.ProcessStatus
import com.intellij.python.processOutput.frontend.formatFull
import com.intellij.python.processOutput.frontend.ui.ProcessOutputUiContext
import com.intellij.python.processOutput.frontend.ui.commandString
import com.intellij.ui.JBColor
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Point
import java.awt.Rectangle
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JViewport
import javax.swing.ScrollPaneConstants
import javax.swing.Scrollable
import javax.swing.SwingUtilities

internal class OutputConsole(private val uiContext: ProcessOutputUiContext) {
  private val infoSection = CollapsibleConsoleSection(
    title = message("process.output.output.sections.info"),
    name = Naming.INFO_SECTION_NAME,
    formatter = InfoTag.formatter,
    onToggle = { uiContext.controller.toggleProcessInfo() },
  )

  private val outputSection = CollapsibleConsoleSection(
    title = message("process.output.output.sections.output"),
    name = Naming.OUTPUT_SECTION_NAME,
    formatter = OutputTag.formatter,
    onToggle = { uiContext.controller.toggleProcessOutput() },
    onCopy = this::onCopy,
  )

  private val contentPanel: ContentPanel = ContentPanel()
  private val scrollPane: JScrollPane = ScrollPaneFactory.createScrollPane(contentPanel, true)
  private var linesJob: Job? = null

  val component: JComponent
    field = JPanel(BorderLayout())

  init {
    contentPanel.isOpaque = false
    contentPanel.layout = BoxLayout(contentPanel, BoxLayout.Y_AXIS)
    contentPanel.border = 
      JBUI.Borders.empty(
        Styling.CONTENT_PANEL_PADDING,
        Styling.CONTENT_PANEL_PADDING,
        Styling.CONTENT_PANEL_PADDING,
        Styling.CONTENT_PANEL_PADDING_EAST,
      )
    contentPanel.withEmptyText(message("process.output.output.blankMessage"))

    component.add(scrollPane)

    uiContext.coroutineScope.launch(Dispatchers.EDT) {
      uiContext.controller.selectedProcess.collect { loggedProcess ->
        linesJob?.cancelAndJoin()
        linesJob = null

        if (loggedProcess == null) {
          contentPanel.removeAll()
        }
        else {
          infoSection.setLines(buildInfoLines(loggedProcess.data))

          linesJob =
            this@launch.launch(Dispatchers.EDT) {
              combine(
                loggedProcess.lines,
                loggedProcess.status,
              ) { lines, status ->
                buildOutputLines(lines, status)
              }
                .collect {
                  outputSection.setLines(it)
                }
            }

          if (contentPanel.componentCount == 0) {
            contentPanel.add(infoSection.component)
            contentPanel.add(outputSection.component)
          }
        }

        contentPanel.revalidate()
        contentPanel.repaint()

        scrollPane.viewport.viewPosition = Point(0, 0)
      }
    }

    uiContext.coroutineScope.launch(Dispatchers.EDT) {
      uiContext.controller.outputSectionState.isInfoExpanded.collect {
        infoSection.setExpanded(it)
      }
    }

    uiContext.coroutineScope.launch(Dispatchers.EDT) {
      uiContext.controller.outputSectionState.isOutputExpanded.collect {
        outputSection.setExpanded(it)
      }
    }

    uiContext.coroutineScope.launch(Dispatchers.EDT) {
      uiContext.controller.outputSectionState.filters.active
        .collect { active ->
          val showTags = OutputFilter.Item.SHOW_TAGS in active
          val wrap = OutputFilter.Item.WRAP_CONTENT in active

          outputSection.setShowTags(showTags)
          infoSection.setWrapContent(wrap)
          outputSection.setWrapContent(wrap)

          contentPanel.wrapContent = wrap
          scrollPane.horizontalScrollBarPolicy =
            if (wrap) {
              ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
            }
            else {
              ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED
            }

          contentPanel.revalidate()
        }
    }

    uiContext.coroutineScope.launch(Dispatchers.EDT) {
      uiContext.controller.events.collect { event ->
        when (event) {
          is ProcessOutputController.Event.StatusUpdate -> {}
          ProcessOutputController.Event.OutputScrollDown -> {
            SwingUtilities.invokeLater {
              val view = scrollPane.viewport.view
              val y = maxOf(0, view.height - scrollPane.viewport.height)

              scrollPane.viewport.viewPosition = Point(0, y)
            }
          }
        }
      }
    }
  }

  private fun onCopy(line: ConsoleTextLine<OutputTag>, index: Int) {
    val loggedProcess = uiContext.controller.selectedProcess.value ?: return

    when (line.tag) {
      OutputTag.EXIT ->
        uiContext.controller.copyOutputExitInfoToClipboard(loggedProcess)
      OutputTag.OUTPUT, OutputTag.ERROR ->
        uiContext.controller.copyOutputTagAtIndexToClipboard(loggedProcess, index)
    }
  }

  private fun buildInfoLines(data: LoggedProcessDto): List<ConsoleTextLine<InfoTag>> =
    buildList {
      add(ConsoleTextLine(InfoTag.STARTED, data.startedAt.formatFull()))
      add(ConsoleTextLine(InfoTag.COMMAND, data.commandString))
      data.pid?.also { pid -> add(ConsoleTextLine(InfoTag.PID, pid.toString())) }
      data.cwd?.also { cwd -> add(ConsoleTextLine(InfoTag.CWD, cwd)) }
      add(ConsoleTextLine(InfoTag.TARGET, data.target))

      for ((key, value) in data.env.entries) {
        add(ConsoleTextLine(InfoTag.ENV, "$key=$value"))
      }
    }

  private fun buildOutputLines(
    lines: List<OutputLineDto>,
    status: ProcessStatus,
  ): List<ConsoleTextLine<OutputTag>> =
    buildList {
      for ((kind, text) in lines) {
        val tag = when (kind) {
          OutputKindDto.OUT -> OutputTag.OUTPUT
          OutputKindDto.ERR -> OutputTag.ERROR
        }

        add(ConsoleTextLine(tag, text))
      }

      when (status) {
        ProcessStatus.Running -> {}
        is ProcessStatus.Done -> {
          val color = Styling.ERROR_FOREGROUND.takeIf { status.exitCode != 0 }
          val exitText = buildString {
            append(status.exitCode)
            status.additionalMessageToUser?.also { messageToUser ->
              append(": ")
              append(messageToUser)
            }
          }

          add(ConsoleTextLine(OutputTag.EXIT, exitText, color))
        }
      }
    }

  private object Styling {
    const val CONTENT_PANEL_PADDING = 8
    const val CONTENT_PANEL_PADDING_EAST = 12
    const val SCROLLABLE_UNIT_INCREMENT = 16
    const val SCROLLABLE_BLOCK_INCREMENT = 100
    val ERROR_FOREGROUND = JBColor.namedColor("Label.errorForeground")
  }

  private object Naming {
    const val INFO_SECTION_NAME = "Python.ProcessOutput.Output.Info"
    const val OUTPUT_SECTION_NAME = "Python.ProcessOutput.Output.Output"
  }

  private class ContentPanel : JBPanelWithEmptyText(), Scrollable {
    var wrapContent: Boolean = false

    override fun getPreferredScrollableViewportSize(): Dimension =
      preferredSize

    override fun getScrollableUnitIncrement(visibleRect: Rectangle?, orientation: Int, direction: Int): Int =
      Styling.SCROLLABLE_UNIT_INCREMENT

    override fun getScrollableBlockIncrement(visibleRect: Rectangle?, orientation: Int, direction: Int): Int =
      Styling.SCROLLABLE_BLOCK_INCREMENT

    override fun getScrollableTracksViewportWidth(): Boolean {
      if (wrapContent || componentCount == 0) {
        return true
      }

      val vp = parent as? JViewport ?: return false

      return preferredSize.width <= vp.width
    }

    override fun getScrollableTracksViewportHeight(): Boolean =
      componentCount == 0
  }
}
