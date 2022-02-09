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
import org.intellij.plugins.markdown.extensions.jcef.commandRunner.CommandRunnerExtension
import org.intellij.plugins.markdown.ui.preview.html.DefaultCodeFenceGeneratingProvider.Companion.escape

internal class CodeSpanRunnerGeneratingProvider(val generatingProvider: GeneratingProvider,
                                                val project: Project,
                                                val file: VirtualFile) : TrimmingInlineHolderProvider() {

  override fun processNode(visitor: HtmlGenerator.HtmlGeneratingVisitor, text: String, node: ASTNode) {
    val codeViewExtension = CommandRunnerExtension.getRunnerByFile(file)
    if (codeViewExtension == null ) {
      generatingProvider.processNode(visitor, text, node)
      return
    }

    var codeLine = ""
    for (child in childrenToRender(node)) {
      if (child is LeafASTNode) { when (child.type) {
          MarkdownTokenTypes.BACKTICK -> continue
          else -> {
            codeLine += text.substring(child.startOffset, child.endOffset)
          }
        }
      }
    }
    visitor.consumeTagOpen(node, "code")
    visitor.consumeHtml(codeViewExtension.processCodeLine(codeLine, false) + escape(codeLine))
    visitor.consumeTagClose("code")
  }
}