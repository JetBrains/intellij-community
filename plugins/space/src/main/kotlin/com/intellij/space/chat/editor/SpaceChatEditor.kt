package com.intellij.space.chat.editor

import com.intellij.diff.util.FileEditorBase
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.space.messages.SpaceBundle
import com.intellij.util.ui.components.BorderLayoutPanel
import javax.swing.JComponent

private class SpaceChatEditor(project: Project, private val spaceChatFile: SpaceChatFile) : FileEditorBase() {
  private val rootComponent: JComponent = BorderLayoutPanel().addToCenter(spaceChatFile.createMainComponent(project))

  override fun getComponent(): JComponent = rootComponent

  override fun getPreferredFocusedComponent(): JComponent? = null

  override fun getName(): String = SpaceBundle.message("chat.editor.name")

  override fun getFile() = spaceChatFile
}

internal class SpaceChatEditorProvider : FileEditorProvider, DumbAware {
  override fun accept(project: Project, file: VirtualFile): Boolean = file is SpaceChatFile

  override fun createEditor(project: Project, file: VirtualFile): FileEditor = SpaceChatEditor(project, file as SpaceChatFile)

  override fun getEditorTypeId(): String = "SpaceChatEditor"

  override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR
}