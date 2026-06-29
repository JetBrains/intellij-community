package com.intellij.terminal.frontend.editor

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.NonNls

internal class TerminalViewFileEditorProvider : FileEditorProvider, DumbAware {
  override fun accept(project: Project, file: VirtualFile): Boolean {
    return file is TerminalViewVirtualFile
  }

  override fun acceptRequiresReadAction(): Boolean {
    return false
  }

  override fun createEditor(project: Project, file: VirtualFile): FileEditor {
    val terminalFile = file as TerminalViewVirtualFile
    return TerminalViewFileEditor(project, terminalFile)
  }

  override fun getEditorTypeId(): @NonNls String {
    return "terminal-view-editor"
  }

  override fun getPolicy(): FileEditorPolicy {
    return FileEditorPolicy.HIDE_DEFAULT_EDITOR
  }
}