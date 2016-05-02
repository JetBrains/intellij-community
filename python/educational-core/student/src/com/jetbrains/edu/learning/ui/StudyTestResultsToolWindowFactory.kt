package com.jetbrains.edu.learning.ui

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindow


class StudyTestResultsToolWindowFactory: StudyToolWindowFactory() {  
  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    val testResultsToolWindow = StudyTestResultsToolWindow()
    testResultsToolWindow.init()

    toolWindow.isToHideOnEmptyContent = true
    
    val contentManager = toolWindow.contentManager
    val content = contentManager.factory.createContent(testResultsToolWindow, null, false)
    contentManager.addContent(content)

    project.messageBus.connect().subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, getFileEditorManagerListener(toolWindow))
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


