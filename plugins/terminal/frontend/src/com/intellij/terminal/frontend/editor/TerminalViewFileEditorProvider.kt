package com.intellij.terminal.frontend.editor

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManagerKeys
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.NonNls
import org.jetbrains.plugins.terminal.JBTerminalSystemSettingsProvider
import org.jetbrains.plugins.terminal.LocalTerminalDirectRunner
import org.jetbrains.plugins.terminal.ShellStartupOptions
import org.jetbrains.plugins.terminal.vfs.ClassicTerminalSessionEditor
import org.jetbrains.plugins.terminal.vfs.TerminalSessionVirtualFileImpl

internal class TerminalViewFileEditorProvider : FileEditorProvider, DumbAware {
  override fun accept(project: Project, file: VirtualFile): Boolean {
    return file is TerminalViewVirtualFile
  }

  override fun acceptRequiresReadAction(): Boolean {
    return false
  }

  override fun createEditor(project: Project, file: VirtualFile): FileEditor {
    val terminalFile = file as TerminalViewVirtualFile
    if (file.getUserData(FileEditorManagerKeys.CLOSING_TO_REOPEN) == true) {
      return TerminalViewFileEditor(project, terminalFile)
    }
    else {
      // Since there is no API to start Reworked Terminal outside of the terminal tool window,
      // create the Classic terminal there similar to ClassicTerminalSessionEditorProvider
      // TODO: we need to use Reworked Terminal there
      val tempDisposable = Disposer.newDisposable()
      val options = ShellStartupOptions.Builder().build()
      val newWidget = LocalTerminalDirectRunner(project).startShellTerminalWidget(
        tempDisposable,
        options,
        true
      )
      val newFile = TerminalSessionVirtualFileImpl(
        terminalFile.name,
        newWidget,
        JBTerminalSystemSettingsProvider()
      )
      val editor = ClassicTerminalSessionEditor(project, newFile)
      Disposer.dispose(tempDisposable) // newWidget's parent disposable should be changed now
      return editor
    }
  }

  override fun getEditorTypeId(): @NonNls String {
    return "terminal-view-editor"
  }

  override fun getPolicy(): FileEditorPolicy {
    return FileEditorPolicy.HIDE_DEFAULT_EDITOR
  }
}