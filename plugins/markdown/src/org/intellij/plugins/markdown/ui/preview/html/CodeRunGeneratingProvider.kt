// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.ui.preview.html

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.LeafASTNode
import org.intellij.markdown.html.GeneratingProvider
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.html.TrimmingInlineHolderProvider
import org.intellij.plugins.markdown.extensions.MarkdownConfigurableExtension
import org.intellij.plugins.markdown.extensions.jcef.MarkdownCodeViewExtension
import org.intellij.plugins.markdown.ui.preview.html.MarkdownCodeFenceGeneratingProvider.Companion.escape

internal class CodeRunGeneratingProvider(val generatingProvider: GeneratingProvider,
                                         val project: Project,
                                         val file: VirtualFile) : TrimmingInlineHolderProvider() {

  override fun processNode(visitor: HtmlGenerator.HtmlGeneratingVisitor, text: String, node: ASTNode) {
    val codeViewExtension = MarkdownCodeViewExtension.allSorted.firstOrNull {
      if (it is MarkdownConfigurableExtension) {
        it.isEnabled
      }
      else true
    }
    if (codeViewExtension == null ) {
      generatingProvider.processNode(visitor, text, node)
      return
    }

    for (child in childrenToRender(node)) {
      if (child is LeafASTNode) { when (child.type) {
          MarkdownTokenTypes.TEXT -> {
            val codeLine = text.substring(child.startOffset, child.endOffset)
            visitor.consumeTagOpen(node, "code")
            visitor.consumeHtml(codeViewExtension.processCodeLine(escape(codeLine), project, file))
            visitor.consumeTagClose("code")
          }
        }
      }
    }
  }
}