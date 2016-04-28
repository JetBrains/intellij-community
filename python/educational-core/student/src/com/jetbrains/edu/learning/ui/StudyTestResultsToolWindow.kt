package com.jetbrains.edu.learning.ui

import com.intellij.openapi.ui.SimpleToolWindowPanel
import java.awt.BorderLayout
import javax.swing.JPanel


class StudyTestResultsToolWindow: SimpleToolWindowPanel(false) {
  private val studyBrowserWindow = StudyBrowserWindow(false, false)
  fun init() {
    val panel = JPanel(BorderLayout())
    studyBrowserWindow.loadContent("", null)
    panel.add(studyBrowserWindow.panel, BorderLayout.CENTER)
    setContent(panel)
  }

  fun setText(text: String) {
    studyBrowserWindow.loadContent(text, null)
  }
}