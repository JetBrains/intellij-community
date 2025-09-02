package com.intellij.terminal.frontend

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.Navigatable
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.terminal.TerminalBundle
import java.beans.PropertyChangeListener
import javax.swing.JComponent

/**
 * Our fake implementation of the [TextEditor] for the terminal output editor.
 *
 * It is necessary to make [getFile] return null and therefor make [com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl.isValidEditor]
 * return null and do not run the highlighting passes in the terminal editor.
 */
internal class TerminalOutputTextEditor(private val editor: Editor) : TextEditor, UserDataHolderBase() {
  override fun getFile(): VirtualFile? {
    return null
  }

  override fun getEditor(): Editor {
    return editor
  }

  override fun getComponent(): JComponent {
    return editor.component
  }

  override fun getPreferredFocusedComponent(): JComponent? {
    return editor.contentComponent
  }

  override fun getName(): @Nls(capitalization = Nls.Capitalization.Title) String {
    return TerminalBundle.message("terminal.output.editor.title")
  }

  override fun isModified(): Boolean {
    return false
  }

  override fun isValid(): Boolean {
    return true
  }

  override fun dispose() {
  }

  override fun canNavigateTo(navigatable: Navigatable): Boolean {
    return false
  }

  override fun navigateTo(navigatable: Navigatable) {
  }

  override fun setState(state: FileEditorState) {
  }

  override fun addPropertyChangeListener(listener: PropertyChangeListener) {
  }

  override fun removePropertyChangeListener(listener: PropertyChangeListener) {
  }
}