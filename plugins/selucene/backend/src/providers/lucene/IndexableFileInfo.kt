// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.selucene.backend.providers.lucene

import com.intellij.ide.actions.CopyContentRootPathProvider
import com.intellij.ide.actions.CopySourceRootPathProvider
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

class IndexableFileInfo private constructor(val name: String,
                                            val contentRootPath: String,
                                            val sourceRootPath: String,
                                            val absolutePath: String) {
  companion object {
    fun of(virtualFile: VirtualFile, project: Project): IndexableFileInfo {
      val (contentRootPath, sourceRootPath) = runReadAction {
        (CopyContentRootPathProvider().getPathToElement(project, virtualFile, null) ?: virtualFile.name) to
        (CopySourceRootPathProvider().getPathToElement(project, virtualFile, null) ?: virtualFile.name)
      }

      return IndexableFileInfo(virtualFile.nameWithoutExtension, contentRootPath, sourceRootPath, virtualFile.path)
    }
  }
}