// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.ui.split

import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.*
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.util.application
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus

private const val FIRST_EDITOR = "first_editor"
private const val SECOND_EDITOR = "second_editor"
private const val SPLIT_LAYOUT = "split_layout"

@ApiStatus.Internal
abstract class SplitTextEditorProvider(
  private val firstProvider: FileEditorProvider,
  private val secondProvider: FileEditorProvider
): AsyncFileEditorProvider, DumbAware {
  private val editorTypeId = createSplitEditorProviderTypeId(firstProvider.editorTypeId, secondProvider.editorTypeId)

  override fun accept(project: Project, file: VirtualFile): Boolean {
    return firstProvider.accept(project, file) && secondProvider.accept(project, file)
  }

  override fun acceptRequiresReadAction(): Boolean {
    return firstProvider.acceptRequiresReadAction() || secondProvider.acceptRequiresReadAction()
  }

  override fun createEditor(project: Project, file: VirtualFile): FileEditor {
    return createEditorAsync(project = project, file = file).build()
  }

  override fun getEditorTypeId(): String {
    return editorTypeId
  }

  override fun createEditorAsync(project: Project, file: VirtualFile): AsyncFileEditorProvider.Builder {
    val firstBuilder = createEditorBuilder(provider = firstProvider, project = project, file = file, document = null)
    val secondBuilder = createEditorBuilder(provider = secondProvider, project = project, file = file, document = null)
    return object : AsyncFileEditorProvider.Builder() {
      override fun build(): FileEditor {
        return createSplitEditor(firstEditor = firstBuilder.build(), secondEditor = secondBuilder.build())
      }
    }
  }

  override suspend fun createEditorBuilder(project: Project, file: VirtualFile, document: Document?): AsyncFileEditorProvider.Builder {
    val firstBuilder = createEditorBuilderAsync(provider = firstProvider, project = project, file = file, document = null)
    val secondBuilder = createEditorBuilderAsync(provider = secondProvider, project = project, file = file, document = null)
    return object: AsyncFileEditorProvider.Builder() {
      override fun build(): FileEditor {
        return createSplitEditor(firstEditor = firstBuilder.build(), secondEditor = secondBuilder.build())
      }
    }
  }

  protected fun readFirstProviderState(sourceElement: Element, project: Project, file: VirtualFile): FileEditorState? {
    val child = sourceElement.getChild(FIRST_EDITOR) ?: return null
    return firstProvider.readState(/* sourceElement = */ child, /* project = */ project, /* file = */ file)
  }

  protected fun readSecondProviderState(sourceElement: Element, project: Project, file: VirtualFile): FileEditorState? {
    val child = sourceElement.getChild(SECOND_EDITOR) ?: return null
    return secondProvider.readState(/* sourceElement = */ child, /* project = */ project, /* file = */ file)
  }

  protected fun readSplitLayoutState(sourceElement: Element, project: Project, file: VirtualFile): String? {
    return sourceElement.getAttribute(SPLIT_LAYOUT)?.value
  }

  override fun readState(sourceElement: Element, project: Project, file: VirtualFile): FileEditorState {
    val firstState = readFirstProviderState(sourceElement, project, file)
    val secondState = readSecondProviderState(sourceElement, project, file)
    val layoutName = readSplitLayoutState(sourceElement, project, file)
    return SplitFileEditor.MyFileEditorState(/* splitLayout = */ layoutName, /* firstState = */ firstState, /* secondState = */ secondState)
  }

  protected fun writeFirstProviderState(state: FileEditorState?, project: Project, targetElement: Element) {
    val child = Element(FIRST_EDITOR)
    if (state != null) {
      firstProvider.writeState(state, project, child)
      targetElement.addContent(child)
    }
  }

  protected fun writeSecondProviderState(state: FileEditorState?, project: Project, targetElement: Element) {
    val child = Element(SECOND_EDITOR)
    if (state != null) {
      secondProvider.writeState(state, project, child)
      targetElement.addContent(child)
    }
  }

  protected fun writeSplitLayoutState(splitLayout: String?, targetElement: Element) {
    if (splitLayout != null) {
      targetElement.setAttribute(SPLIT_LAYOUT, splitLayout)
    }
  }

  override fun writeState(state: FileEditorState, project: Project, targetElement: Element) {
    if (state !is SplitFileEditor.MyFileEditorState) {
      return
    }
    writeFirstProviderState(state = state.firstState, project = project, targetElement = targetElement)
    writeSecondProviderState(state = state.secondState, project = project, targetElement = targetElement)
    writeSplitLayoutState(splitLayout = state.splitLayout, targetElement = targetElement)
  }

  protected abstract fun createSplitEditor(firstEditor: FileEditor, secondEditor: FileEditor): FileEditor

  override fun getPolicy(): FileEditorPolicy {
    return FileEditorPolicy.HIDE_DEFAULT_EDITOR
  }
}

private fun createEditorBuilder(
  provider: FileEditorProvider,
  project: Project,
  file: VirtualFile,
  document: Document?
): AsyncFileEditorProvider.Builder {
  if (provider is AsyncFileEditorProvider) {
    if (application.isDispatchThread) {
      return runWithModalProgressBlocking(project, title = "Creating Markdown Editor") {
        provider.createEditorBuilder(project = project, file = file, document = document)
      }
    }
    return runBlockingCancellable {
      provider.createEditorBuilder(project = project, file = file, document = document)
    }
  }
  return object: AsyncFileEditorProvider.Builder() {
    override fun build(): FileEditor {
      return provider.createEditor(project, file)
    }
  }
}

private suspend fun createEditorBuilderAsync(
  provider: FileEditorProvider,
  project: Project,
  file: VirtualFile,
  document: Document?,
): AsyncFileEditorProvider.Builder {
  if (provider is AsyncFileEditorProvider) {
    return provider.createEditorBuilder(project = project, file = file, document = document)
  }
  return object: AsyncFileEditorProvider.Builder() {
    override fun build(): FileEditor {
      return provider.createEditor(project, file)
    }
  }
}

@ApiStatus.Internal
fun createSplitEditorProviderTypeId(first: String, second: String): String {
  return "split-provider[$first;$second]"
}
