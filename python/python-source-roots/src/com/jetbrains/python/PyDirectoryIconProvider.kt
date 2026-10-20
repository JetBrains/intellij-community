// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python

import com.intellij.icons.AllIcons
import com.intellij.ide.IconProvider
import com.intellij.ide.projectView.impl.ProjectRootsUtil
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.python.pyproject.icons.PythonPyprojectIcons
import com.intellij.python.pyproject.model.api.isPyProjectTomlBased
import com.jetbrains.python.psi.PyUtil
import javax.swing.Icon

internal class PyDirectoryIconProvider : IconProvider() {
  override fun getIcon(element: PsiElement, flags: Int): Icon? {
    val directory = (element as? PsiDirectory)?.takeIf { it.isValid } ?: return null
    val vFile = directory.virtualFile
    val project = directory.project
    val fileIndex = ProjectFileIndex.getInstance(project)

    if (ProjectRootsUtil.isModuleContentRoot(vFile, project)) {
      val module = fileIndex.getModuleForFile(vFile) ?: return null
      return if (module.isPyProjectTomlBased) {
        PythonPyprojectIcons.Model.PyProjectModule
      }
      else {
        AllIcons.Nodes.Module
      }
    }

    // Preserve original icons for excluded directories and source roots
    if (fileIndex.isExcluded(vFile) || isSpecialDirectory(directory)) return null

    if (PyUtil.isExplicitPackage(directory)) {
      return AllIcons.Nodes.Package
    }
    return null
  }

  private fun isSpecialDirectory(directory: PsiDirectory): Boolean {
    // On large projects, using the ProjectFileIndex here is *noticeably* faster than
    // asking for all source- and content-roots and checking .contains(vFile)
    return ProjectRootsUtil.isSourceRoot(directory) || ProjectRootsUtil.isModuleContentRoot(directory)
  }
}
