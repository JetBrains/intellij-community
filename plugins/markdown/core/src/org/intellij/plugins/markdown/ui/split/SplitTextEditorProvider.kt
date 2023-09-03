// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.ui.split

import com.intellij.openapi.fileEditor.*
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jdom.Element

private const val FIRST_EDITOR = "first_editor"
private const val SECOND_EDITOR = "second_editor"
private const val SPLIT_LAYOUT = "split_layout"

abstract class SplitTextEditorProvider(private val firstProvider: FileEditorProvider,
                                       private val secondProvider: FileEditorProvider) : AsyncFileEditorProvider, DumbAware {
  private val editorTypeId = "split-provider[${firstProvider.getEditorTypeId()};${secondProvider.getEditorTypeId()}]"

  override fun accept(project: Project, file: VirtualFile): Boolean {
    return firstProvider.accept(project, file) && secondProvider.accept(project, file)
  }

  override fun acceptRequiresReadAction(): Boolean {
    return firstProvider.acceptRequiresReadAction() || secondProvider.acceptRequiresReadAction()
  }

  override fun createEditor(project: Project, file: VirtualFile): FileEditor {
    return createEditorAsync(project = project, file = file).build()
  }

  override fun getEditorTypeId(): String = editorTypeId

  override fun createEditorAsync(project: Project, file: VirtualFile): AsyncFileEditorProvider.Builder {
    val firstBuilder = getBuilderFromEditorProvider(provider = firstProvider, project = project, file = file)
    val secondBuilder = getBuilderFromEditorProvider(provider = secondProvider, project = project, file = file)
    return object : AsyncFileEditorProvider.Builder() {
      override fun build(): FileEditor {
        return createSplitEditor(firstEditor = firstBuilder.build(), secondEditor = secondBuilder.build())
      }
    }
  }

  override suspend fun createEditorBuilder(project: Project, file: VirtualFile): AsyncFileEditorProvider.Builder {
    val firstBuilder = getBuilderFromEditorProviderAsync(provider = firstProvider, project = project, file = file)
    val secondBuilder = getBuilderFromEditorProviderAsync(provider = secondProvider, project = project, file = file)
    return object : AsyncFileEditorProvider.Builder() {
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

  override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR
}

private fun getBuilderFromEditorProvider(provider: FileEditorProvider,
                                         project: Project,
                                         file: VirtualFile): AsyncFileEditorProvider.Builder {
  if (provider is AsyncFileEditorProvider) {
    return provider.createEditorAsync(project, file)
  }
  else {
    return object : AsyncFileEditorProvider.Builder() {
      override fun build(): FileEditor {
        return provider.createEditor(project, file)
      }
    }
  }
}

private suspend fun getBuilderFromEditorProviderAsync(provider: FileEditorProvider,
                                                      project: Project,
                                                      file: VirtualFile): AsyncFileEditorProvider.Builder {
  if (provider is AsyncFileEditorProvider) {
    return provider.createEditorBuilder(project, file)
  }
  else {
    return object : AsyncFileEditorProvider.Builder() {
      override fun build(): FileEditor {
        return provider.createEditor(project, file)
      }
    }
  }
}
