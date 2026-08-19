// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.minimap

import com.intellij.ide.minimap.interaction.MinimapInteractionPolicy
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.jetbrains.python.PythonFileType
import com.jetbrains.python.structureView.PyStructureViewElement

internal class PyMinimapInteractionPolicy : MinimapInteractionPolicy {
  override fun isApplicable(editor: Editor): Boolean {
    val vfile = FileDocumentManager.getInstance().getFile(editor.document) ?: return false
    return vfile.fileType == PythonFileType.INSTANCE
  }

  override fun isRelevantStructureElement(element: StructureViewTreeElement, value: Any): Boolean {
    val pyElement = element as? PyStructureViewElement ?: return false
    return !pyElement.isInherited
  }
}
