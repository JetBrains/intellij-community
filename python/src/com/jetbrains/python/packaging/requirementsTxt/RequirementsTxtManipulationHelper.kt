// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.requirementsTxt

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.jetbrains.python.PyBundle
import org.jetbrains.annotations.ApiStatus

internal object RequirementsTxtManipulationHelper {
  @ApiStatus.Internal
  @JvmStatic
  fun addToRequirementsTxt(project: Project, requirementsTxt: VirtualFile, requirementName: String): Boolean {
    if (!requirementsTxt.isWritable()) {
      return false
    }

    val document = FileDocumentManager.getInstance().getDocument(requirementsTxt) ?: return false
    // Write the modified content back to the file
    @Suppress("DialogTitleCapitalization")
    WriteCommandAction.runWriteCommandAction(project, PyBundle.message("command.name.add.package.to.requirements.txt"), null, {
      document.insertString(0, requirementName + "\n")
      FileDocumentManager.getInstance().saveDocument(document)
    }, PsiManager.getInstance(project).findFile(requirementsTxt))

    return true
  }
}