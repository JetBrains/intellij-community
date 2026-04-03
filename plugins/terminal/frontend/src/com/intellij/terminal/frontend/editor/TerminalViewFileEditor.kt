package com.intellij.terminal.frontend.editor

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerKeys
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.util.coroutines.childScope
import com.intellij.terminal.frontend.toolwindow.impl.updateFileNameOnTitleChange
import com.intellij.terminal.frontend.view.TerminalView
import com.intellij.terminal.frontend.view.TerminalViewSessionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.terminal.util.terminalProjectScope
import java.beans.PropertyChangeListener
import javax.swing.JComponent

@OptIn(FlowPreview::class)
internal class TerminalViewFileEditor(
  private val project: Project,
  private val file: TerminalViewVirtualFile,
) : FileEditor, UserDataHolderBase() {
  private val terminalView: TerminalView
    get() = file.terminalView

  init {
    val coroutineScope = terminalProjectScope(project).childScope("TerminalViewFileEditor")
    Disposer.register(this) { coroutineScope.cancel() }

    if (file.closeOnProcessTermination) {
      coroutineScope.launch {
        terminalView.sessionState.collect { state ->
          if (state == TerminalViewSessionState.Terminated) {
            // Execute in the separate scope, because closing of the file may dispose the file editor and cancel the current coroutine.
            terminalProjectScope(project).launch(Dispatchers.EDT + ModalityState.any().asContextElement()) {
              project.serviceAsync<FileEditorManager>().closeFile(file)
            }
          }
        }
      }
    }

    updateFileNameOnTitleChange(
      terminalView,
      file,
      project,
      coroutineScope,
    )
  }

  override fun getComponent(): JComponent {
    return terminalView.component
  }

  override fun getPreferredFocusedComponent(): JComponent {
    return terminalView.preferredFocusableComponent
  }

  override fun getName(): @Nls(capitalization = Nls.Capitalization.Title) String {
    return file.name
  }

  override fun getFile(): VirtualFile {
    return file
  }

  override fun isModified(): Boolean {
    return false
  }

  override fun isValid(): Boolean {
    return true
  }

  override fun dispose() {
    if (file.getUserData(FileEditorManagerKeys.CLOSING_TO_REOPEN) != true) {
      terminalView.coroutineScope.cancel()
    }
  }

  override fun addPropertyChangeListener(listener: PropertyChangeListener) {}

  override fun removePropertyChangeListener(listener: PropertyChangeListener) {}

  override fun setState(state: FileEditorState) {}
}