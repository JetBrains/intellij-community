package com.jetbrains.edu.learning.ui

import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.jetbrains.edu.learning.StudyUtils


@JvmField val ID = "Test Results"
class StudyTestResultsToolWindowFactory : ToolWindowFactory {
  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    val currentTask = StudyUtils.getCurrentTask(project)
    if (currentTask != null) {
      val consoleView = ConsoleViewImpl(project, true);
      toolWindow.isToHideOnEmptyContent = true

      val contentManager = toolWindow.contentManager
      val content = contentManager.factory.createContent(consoleView.component, null, false)
      contentManager.addContent(content)
      val editor = consoleView.editor
      if (editor is EditorEx) {
        editor.isRendererMode = true
      }

      project.messageBus.connect().subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, getFileEditorManagerListener(toolWindow))
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


