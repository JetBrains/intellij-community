// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.jcef.preview

import com.intellij.codeInsight.multiverse.anyContext
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import org.intellij.plugins.markdown.extensions.jcef.commandRunner.MarkdownCommandRunnerPreviewExtension
import org.intellij.plugins.markdown.lang.parser.MarkdownParserManager
import org.intellij.plugins.markdown.lang.parser.obtainFlavourOrNull
import org.intellij.plugins.markdown.ui.preview.SourceTextPreprocessor
import org.intellij.plugins.markdown.ui.preview.html.MarkdownUtil.generateMarkdownHtml

class HtmlSourceTextPreprocessor : SourceTextPreprocessor {
  override fun preprocessText(project: Project, document: Document, file: VirtualFile): String {
    MarkdownCommandRunnerPreviewExtension.getByFile(file)?.resetCommands()
    val flavour = PsiDocumentManager.getInstance(project).getPsiFile(document, anyContext())?.let(::obtainFlavourOrNull)
                  ?: MarkdownParserManager.FLAVOUR
    val html = generateMarkdownHtml(file, document.text, project, flavour)
    return "<html><head></head>$html</html>"
  }
}
