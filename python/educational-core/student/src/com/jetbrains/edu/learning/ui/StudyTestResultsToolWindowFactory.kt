package com.jetbrains.edu.learning.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow


class StudyTestResultsToolWindowFactory: StudyToolWindowFactory() {  
  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    val testResultsToolWindow = StudyTestResultsToolWindow()
    testResultsToolWindow.init()
    
    val contentManager = toolWindow.contentManager
    val content = contentManager.factory.createContent(testResultsToolWindow, null, false)
    contentManager.addContent(content)
  }
}


