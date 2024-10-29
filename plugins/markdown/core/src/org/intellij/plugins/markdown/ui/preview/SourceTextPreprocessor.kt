// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.ui.preview

import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

interface SourceTextPreprocessor {
  fun preprocessText(project: Project, document: Document, file: VirtualFile): String

  companion object {
    @JvmStatic
    val default: SourceTextPreprocessor = object : SourceTextPreprocessor {
      override fun preprocessText(project: Project, document: Document, file: VirtualFile): String {
        return document.text
      }
    }
  }
}
