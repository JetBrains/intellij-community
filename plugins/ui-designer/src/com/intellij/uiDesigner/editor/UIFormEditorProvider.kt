// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.editor

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.AsyncFileEditorProvider
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import com.intellij.uiDesigner.GuiFormFileType
import com.intellij.util.ArrayUtilRt
import com.intellij.util.SlowOperations
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jdom.Element

internal class UIFormEditorProvider : FileEditorProvider, AsyncFileEditorProvider {
  override fun accept(project: Project, file: VirtualFile): Boolean {
    SlowOperations.knownIssue("IDEA-307701, EA-762786").use {
      return FileTypeRegistry.getInstance().isFileOfType(file, GuiFormFileType.INSTANCE) &&
             !GuiFormFileType.INSTANCE.isBinary &&
             (ModuleUtilCore.findModuleForFile(file, project) != null || file is LightVirtualFile)
    }
  }

  override suspend fun createFileEditor(
    project: Project,
    file: VirtualFile,
    document: Document?,
    editorCoroutineScope: CoroutineScope,
  ): FileEditor {
    val effectiveFile = if (file is LightVirtualFile) file.originalFile else file
    val projectFileIndex = ProjectFileIndex.getInstance(project)
    val module = readAction { projectFileIndex.getModuleForFile(effectiveFile) }
    requireNotNull(module) { "No module for file $effectiveFile in project $project" }
    return withContext(Dispatchers.EDT) {
      UIFormEditor(project, effectiveFile, module)
    }
  }

  override fun createEditor(project: Project, file: VirtualFile): FileEditor = UIFormEditor(project, file)

  override fun readState(element: Element, project: Project, file: VirtualFile): FileEditorState {
    //TODO[anton,vova] implement
    return MyEditorState(-1, ArrayUtilRt.EMPTY_STRING_ARRAY)
  }

  override fun getEditorTypeId(): String = "ui-designer"

  override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR
}