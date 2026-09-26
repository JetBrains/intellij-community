package org.intellij.plugins.markdown.lang.parser

import com.intellij.openapi.util.registry.Registry
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.html.GeneratingProvider
import org.intellij.markdown.html.HtmlGenerator

internal class FrontMatterGeneratingProvider: GeneratingProvider {
  override fun processNode(visitor: HtmlGenerator.HtmlGeneratingVisitor, text: String, node: ASTNode) {
    if (!Registry.`is`("markdown.experimental.show.frontmatter.in.preview", false)) {
      return
    }
    visitor.consumeTagOpen(node, "pre", """class="frontmatter-header"""")
    visitor.consumeHtml(node.getTextInNode(text))
    visitor.consumeTagClose("pre")
  }
}
