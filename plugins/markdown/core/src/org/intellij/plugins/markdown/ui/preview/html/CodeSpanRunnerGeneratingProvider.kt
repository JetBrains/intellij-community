// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.ui.preview.html

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.flavours.gfm.TableAwareCodeSpanGeneratingProvider
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.plugins.markdown.extensions.jcef.commandRunner.CommandRunnerExtension

internal class CodeSpanRunnerGeneratingProvider(
  val project: Project,
  val file: VirtualFile
): TableAwareCodeSpanGeneratingProvider() {
  override fun processNode(visitor: HtmlGenerator.HtmlGeneratingVisitor, text: String, node: ASTNode) {
    val codeViewExtension = CommandRunnerExtension.getRunnerByFile(file)
    if (codeViewExtension == null ) {
      super.processNode(visitor, text, node)
      return
    }
    val isInsideTable = isInsideTable(node)
    val nodes = collectContentNodes(node)
    val content = nodes.joinToString(separator = "") { processChild(it, text, isInsideTable) }.trim()
    val lineRunner = codeViewExtension.processCodeLine(content, false)
    visitor.consumeTagOpen(node, "code")
    visitor.consumeHtml("$lineRunner$content")
    visitor.consumeTagClose("code")
  }
}
