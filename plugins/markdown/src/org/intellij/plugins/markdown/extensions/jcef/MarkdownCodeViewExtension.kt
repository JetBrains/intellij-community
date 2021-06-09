// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.extensions.jcef

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.intellij.plugins.markdown.extensions.MarkdownConfigurableExtension


interface MarkdownCodeViewExtension : MarkdownJCEFPreviewExtension {

  val codeEvents: Map<String, (String, Project, VirtualFile) -> Unit>
    get() = emptyMap()

  //fun openTag(escapedCodeLine: String, project: Project?, file: VirtualFile?): String
  //fun closeTag(escapedCodeLine: String, project: Project?, file: VirtualFile?): String

  fun processCodeLine(escapedCodeLine: String, project: Project?, file: VirtualFile?): String


  companion object {
    @JvmStatic
    val all: List<MarkdownCodeViewExtension> =
      MarkdownJCEFPreviewExtension.all.filterIsInstance<MarkdownCodeViewExtension>()

    @JvmStatic
    val allSorted: List<MarkdownCodeViewExtension> =
      MarkdownJCEFPreviewExtension.allSorted.filterIsInstance<MarkdownCodeViewExtension>()

    fun processCodeLine(escapedCodeLine: String, project: Project?, file: VirtualFile?): String {
      val codeViewExtension = allSorted.firstOrNull {
        if (it is MarkdownConfigurableExtension) {
          it.isEnabled
        }
        else true
      }
      return codeViewExtension?.processCodeLine(escapedCodeLine, project, file) ?: escapedCodeLine
    }
  }
}
