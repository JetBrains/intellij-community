// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.vcs.review.details.diff

import circlet.client.codeView
import com.intellij.diff.DiffContentFactory
import com.intellij.diff.chains.DiffRequestProducer
import com.intellij.diff.impl.CacheDiffRequestProcessor
import com.intellij.diff.requests.DiffRequest
import com.intellij.diff.requests.LoadingDiffRequest
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.diff.util.FileEditorBase
import com.intellij.execution.process.ProcessIOExecutorService
import com.intellij.openapi.Disposable
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.space.messages.SpaceBundle
import com.intellij.space.vcs.review.details.getFileContent
import com.intellij.space.vcs.review.details.getFilePath
import kotlinx.coroutines.asCoroutineDispatcher
import libraries.coroutines.extra.LifetimeSource
import libraries.coroutines.extra.runBlocking
import javax.swing.JComponent

internal class SpaceDiffFileEditor(project: Project, spaceDiffFile: SpaceDiffFile) : FileEditorBase() {
  private val diffEditorLifeTime = LifetimeSource()

  private val diffProcessor = DiffProcessor(project, spaceDiffFile)

  init {
    Disposer.register(this, Disposable { diffEditorLifeTime.terminate() })

    val detailsDetailsVm = spaceDiffFile.detailsDetailsVm
    detailsDetailsVm.selectedChange.forEach(diffEditorLifeTime) {
      diffProcessor.updateRequest()
    }
  }

  override fun getComponent(): JComponent = diffProcessor.component

  override fun getPreferredFocusedComponent(): JComponent? = diffProcessor.preferredFocusedComponent

  override fun getName(): String = SpaceBundle.message("review.diff.editor.name")
}

internal class SpaceDiffEditorProvider : FileEditorProvider, DumbAware {
  override fun accept(project: Project, file: VirtualFile): Boolean = file is SpaceDiffFile

  override fun createEditor(project: Project, file: VirtualFile): FileEditor = SpaceDiffFileEditor(project, file as SpaceDiffFile)

  override fun getEditorTypeId(): String = "SpaceDiffEditor"

  override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR
}


internal class DiffProcessor(project: Project, val spaceDiffFile: SpaceDiffFile) : CacheDiffRequestProcessor.Simple(project) {

  override fun getCurrentRequestProvider(): DiffRequestProducer {

    return object : DiffRequestProducer {
      val coroutineContext = ProcessIOExecutorService.INSTANCE.asCoroutineDispatcher()

      override fun getName(): String = "DiffRequestProducer"

      override fun process(context: UserDataHolder, indicator: ProgressIndicator): DiffRequest {
        val detailsDetailsVm = spaceDiffFile.detailsDetailsVm

        val change = detailsDetailsVm.selectedChange.value ?: return LoadingDiffRequest("")
        val codeViewService = detailsDetailsVm.client.codeView
        val projectKey = detailsDetailsVm.projectKey
        val selectedCommitHashes = detailsDetailsVm.commits.value
          ?.map { it.commitWithGraph }
          ?.filter { it.repositoryName == change.repository }
          ?.map { it.commit.id }
        if (selectedCommitHashes != null) {

          return runBlocking(detailsDetailsVm.lifetime, coroutineContext) {
            val sideBySideDiff = codeViewService.getSideBySideDiff(projectKey,
                                                                   change.repository,
                                                                   change.change, false, selectedCommitHashes)


            val leftFileText = getFileContent(sideBySideDiff.left)
            val rightFileText = getFileContent(sideBySideDiff.right)

            val contents = listOf(leftFileText, rightFileText).map {
              DiffContentFactory.getInstance().create(
                project,
                it,
                null,
                true
              )
            }.toList()

            val titles = listOf(selectedCommitHashes.first(),
                                selectedCommitHashes.last())

            return@runBlocking SimpleDiffRequest(getFilePath(change).toString(), contents, titles)
          }
        }
        else return LoadingDiffRequest("")
      }
    }
  }
}