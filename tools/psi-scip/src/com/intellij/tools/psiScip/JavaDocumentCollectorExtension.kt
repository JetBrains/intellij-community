// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.psiScip

import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiJavaFile
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes

class JavaDocumentCollectorExtension : ScipDocumentCollector {
  override val fileExtensions: Set<String> = setOf("java")
  override val requiresJdk: Boolean = true

  override fun isSourceFile(file: VirtualFile, fileIndex: ProjectFileIndex): Boolean = isUnderJvmSourceRoot(file, fileIndex)

  override fun accepts(file: PsiFile): Boolean = file is PsiJavaFile

  override fun collect(file: PsiFile, relativePath: String): ScipDocumentCollection {
    val javaFile = file as? PsiJavaFile ?: error("expected Java PSI for $relativePath")
    val collector = JavaDocumentCollector(javaFile, relativePath)
    return ScipDocumentCollection(collector.collect(), collector.stats)
  }
}

/** A Java source root of the production or the test type; a Kotlin source root is one, a resource root such as `testData` is not. */
internal fun isUnderJvmSourceRoot(file: VirtualFile, fileIndex: ProjectFileIndex): Boolean =
  fileIndex.isUnderSourceRootOfType(file, JavaModuleSourceRootTypes.SOURCES)
