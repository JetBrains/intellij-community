// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.psiScip

import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.psi.KtFile

class KotlinDocumentCollectorExtension : ScipDocumentCollector {
  override val fileExtensions: Set<String> = setOf("kt")
  // Kotlin resolves its JDK types and the Java sources it calls through the same JDK as Java
  override val requiresJdk: Boolean = true

  override fun isSourceFile(file: VirtualFile, fileIndex: ProjectFileIndex): Boolean = isUnderJvmSourceRoot(file, fileIndex)

  override fun accepts(file: PsiFile): Boolean = file is KtFile

  override fun collect(file: PsiFile, relativePath: String): ScipDocumentCollection {
    val ktFile = file as? KtFile ?: error("expected Kotlin PSI for $relativePath")
    val collector = KotlinDocumentCollector(ktFile, relativePath)
    return ScipDocumentCollection(collector.collect(), collector.stats)
  }
}
