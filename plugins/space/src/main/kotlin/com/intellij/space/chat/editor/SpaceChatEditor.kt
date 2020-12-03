// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.chat.editor

import com.intellij.diff.util.FileEditorBase
import com.intellij.openapi.Disposable
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.space.chat.ui.SpaceChatPanel
import com.intellij.space.messages.SpaceBundle
import libraries.coroutines.extra.LifetimeSource
import javax.swing.JComponent

private class SpaceChatEditor(private val project: Project, private val spaceChatFile: SpaceChatFile) : FileEditorBase() {
  private val editorLifetime = LifetimeSource()

  init {
    Disposer.register(this, Disposable { editorLifetime.terminate() })
  }

  val component by lazy {
    val headerDetails = spaceChatFile.headerDetailsBuilder(editorLifetime)
    SpaceChatPanel(project, editorLifetime, this, spaceChatFile.channelsVm, spaceChatFile.chatRecord, headerDetails)
  }

  override fun getComponent(): JComponent = component

  override fun getPreferredFocusedComponent(): JComponent? = null

  override fun getName(): String = SpaceBundle.message("chat.editor.name")

  override fun getFile() = spaceChatFile
}

internal class SpaceChatEditorProvider : FileEditorProvider, DumbAware {
  override fun accept(project: Project, file: VirtualFile): Boolean = file is SpaceChatFile

  override fun createEditor(project: Project, file: VirtualFile): FileEditor = SpaceChatEditor(project, file as SpaceChatFile)

  override fun getEditorTypeId(): String = "SpaceChatEditor"

  override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR

  override fun disposeEditor(editor: FileEditor) {
    if (editor.file?.getUserData(FileEditorManagerImpl.CLOSING_TO_REOPEN) != true) {
      Disposer.dispose(editor)
    }

    super.disposeEditor(editor)
  }
}