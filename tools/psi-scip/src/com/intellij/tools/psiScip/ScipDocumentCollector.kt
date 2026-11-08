// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.psiScip

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile

/** A language-specific PSI collector registered by the language plugin. */
interface ScipDocumentCollector {
  val fileExtensions: Set<String>
  val requiresJdk: Boolean get() = false

  fun configureProject(project: Project) {}

  fun isSourceFile(file: VirtualFile, fileIndex: ProjectFileIndex): Boolean = fileIndex.getSourceRootForFile(file) != null

  fun accepts(file: PsiFile): Boolean

  fun collect(file: PsiFile, relativePath: String): ScipDocumentCollection

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<ScipDocumentCollector> =
      ExtensionPointName.create("com.intellij.tools.psiScip.documentCollector")
  }
}

class ScipDocumentCollection(@JvmField val document: ScipDocument?, @JvmField val stats: ExportStats)