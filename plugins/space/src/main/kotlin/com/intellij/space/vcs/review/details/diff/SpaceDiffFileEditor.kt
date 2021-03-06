// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.vcs.review.details.diff

import com.intellij.diff.editor.DiffRequestProcessorEditorCustomizer
import com.intellij.diff.util.FileEditorBase
import com.intellij.openapi.Disposable
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.impl.EditorWindow
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.space.messages.SpaceBundle
import com.intellij.space.vcs.review.details.SpaceReviewChangesVm
import libraries.coroutines.extra.LifetimeSource
import runtime.reactive.bind
import javax.swing.JComponent

internal class SpaceDiffFileEditor(project: Project, spaceDiffFile: SpaceDiffFile) : FileEditorBase() {
  internal val diffProcessor = SpaceDiffRequestProcessor(project)

  private val editorLifetime = LifetimeSource()

  init {
    Disposer.register(this, Disposable { editorLifetime.terminate() })
    Disposer.register(this, diffProcessor)

    editorLifetime.bind(spaceDiffFile.spaceDiffFileData) { spaceDiffFileData ->
      val (changesVmProperty, spaceDiffVm) = spaceDiffFileData ?: return@bind
      bind(changesVmProperty) { changesVm: SpaceReviewChangesVm ->
        val chainBuilder = SpaceDiffRequestChainBuilder(lifetime, project, spaceDiffVm)

        bind(changesVm.changes) {
          bind(changesVm.selectedChanges) { selectedChange ->
            diffProcessor.chain = chainBuilder.getRequestChain(selectedChange)
          }
        }
      }
    }
  }

  override fun isValid(): Boolean = !Disposer.isDisposed(diffProcessor)

  override fun getComponent(): JComponent = diffProcessor.component

  override fun getPreferredFocusedComponent(): JComponent? = diffProcessor.preferredFocusedComponent

  override fun getName(): String = SpaceBundle.message("review.diff.editor.name")
}

internal class SpaceDiffEditorProvider : FileEditorProvider, DumbAware {
  override fun accept(project: Project, file: VirtualFile): Boolean = file is SpaceDiffFile

  override fun createEditor(project: Project, file: VirtualFile): FileEditor =
    SpaceDiffFileEditor(project, file as SpaceDiffFile).also { editor ->
      editor.putUserData(EditorWindow.HIDE_TABS, true)
      DiffRequestProcessorEditorCustomizer.customize(file, editor, editor.diffProcessor)
    }

  override fun getEditorTypeId(): String = "SpaceDiffEditor"

  override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR
}
