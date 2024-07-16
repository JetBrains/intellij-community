// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.dom

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface MavenAdditionalHightligher {
  fun resolveModulePsi(text: String, psiFile: PsiFile, virtualFile: VirtualFile): PsiFile?

  companion object {
    @JvmField
    val EP = ExtensionPointName<MavenAdditionalHightligher>("org.jetbrains.idea.maven.mavenAdditionalHighlighter")
  }

}