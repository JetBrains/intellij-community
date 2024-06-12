// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.editor

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import com.intellij.uiDesigner.GuiFormFileType
import com.intellij.util.ArrayUtilRt
import com.intellij.util.SlowOperations
import org.jdom.Element

private class UIFormEditorProvider : FileEditorProvider, DumbAware {
  override fun accept(project: Project, file: VirtualFile): Boolean {
    SlowOperations.knownIssue("IDEA-307701, EA-762786").use {
      return FileTypeRegistry.getInstance().isFileOfType(file, GuiFormFileType.INSTANCE) &&
             !GuiFormFileType.INSTANCE.isBinary &&
             (ModuleUtilCore.findModuleForFile(file, project) != null || file is LightVirtualFile)
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