package com.intellij.mermaid.test

import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.accept
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.ast.visitors.RecursiveVisitor
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.parser.MarkdownParser

private fun parseDocument(text: String): ASTNode {
  val parser = MarkdownParser(GFMFlavourDescriptor())
  return parser.parse(MarkdownElementTypes.MARKDOWN_FILE, text, parseInlines = true)
}

internal fun collectExamples(text: String): List<CharSequence> {
  val tree = parseDocument(text)
  return buildList {
    tree.accept(object: RecursiveVisitor() {
      override fun visitNode(node: ASTNode) {
        if (node.type == MarkdownElementTypes.CODE_FENCE) {
          val infoString = node.obtainInfoString(text)
          if (infoString == "mermaid-example") {
            add(node.collectFenceContent(text))
          }
        }
        super.visitNode(node)
      }
    })
  }
}

private fun ASTNode.obtainInfoString(fileText: String): CharSequence? {
  require(type == MarkdownElementTypes.CODE_FENCE)
  return children.firstOrNull { it.type == MarkdownTokenTypes.FENCE_LANG }?.getTextInNode(fileText)
}

private fun ASTNode.collectFenceContent(fileText: String): String {
  require(type == MarkdownElementTypes.CODE_FENCE)
  val elementsToSkip = setOf(
    MarkdownTokenTypes.CODE_FENCE_START,
    MarkdownTokenTypes.FENCE_LANG,
    MarkdownTokenTypes.CODE_FENCE_END
  )
  val elements = children.asSequence().filterNot { it.type in elementsToSkip }
  val content = elements.joinToString(separator = "") { it.getTextInNode(fileText) }
  return content.removePrefix("\n")
}

// private fun ASTNode.print(depth: Int = 0) {
//   println("""${" ".repeat(depth)} $type""")
//   for (child in children) {
//     child.print(depth + 1)
//   }
// }
