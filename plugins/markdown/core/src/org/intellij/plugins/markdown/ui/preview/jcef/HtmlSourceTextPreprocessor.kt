// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.ui.preview.jcef

import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.intellij.plugins.markdown.ui.preview.SourceTextPreprocessor
import org.intellij.plugins.markdown.ui.preview.html.MarkdownUtil.generateMarkdownHtml

class HtmlSourceTextPreprocessor : SourceTextPreprocessor {
  override fun preprocessText(project: Project, document: Document, file: VirtualFile): String {
    val html = generateMarkdownHtml(file, document.text, project)
    return "<html><head></head>$html</html>"
  }
}
