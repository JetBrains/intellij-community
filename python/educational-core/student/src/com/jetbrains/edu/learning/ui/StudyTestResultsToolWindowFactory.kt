package com.jetbrains.edu.learning.ui

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.jetbrains.edu.learning.StudyUtils
import com.jetbrains.python.console.PythonConsoleView


class StudyTestResultsToolWindowFactory: ToolWindowFactory {  
  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    val currentTask = StudyUtils.getCurrentTask(project)
    if (currentTask != null) {
      val sdk = StudyUtils.findSdk(currentTask, project)
      if (sdk != null) {
        val testResultsToolWindow = PythonConsoleView(project, "Local test results", sdk);
        testResultsToolWindow.isEditable = false
        testResultsToolWindow.isConsoleEditorEnabled = false
        testResultsToolWindow.prompt = null
        toolWindow.isToHideOnEmptyContent = true

        val contentManager = toolWindow.contentManager
        val content = contentManager.factory.createContent(testResultsToolWindow.component, null, false)
        contentManager.addContent(content)

        project.messageBus.connect().subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, getFileEditorManagerListener(toolWindow))
      }
      else {
        StudyUtils.showNoSdkNotification(currentTask, project)
      }
    }
  }

  fun getFileEditorManagerListener(toolWindow: ToolWindow): FileEditorManagerListener {

    return object : FileEditorManagerListener {

      override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
      }

      override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
        toolWindow.setAvailable(false, {})
      }

      override fun selectionChanged(event: FileEditorManagerEvent) {
        toolWindow.setAvailable(false, {})
      }
    }
  }
}


