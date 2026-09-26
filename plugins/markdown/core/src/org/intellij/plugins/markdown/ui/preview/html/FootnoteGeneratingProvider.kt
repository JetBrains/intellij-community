// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.ui.preview.html

import com.intellij.openapi.progress.ProgressManager
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.accept
import org.intellij.markdown.ast.findChildOfType
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.ast.visitors.RecursiveVisitor
import org.intellij.markdown.html.GeneratingProvider
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.parser.LinkMap
import org.intellij.plugins.markdown.injection.MarkdownCodeFenceUtils
import org.intellij.plugins.markdown.lang.parser.CancellableText
import org.intellij.plugins.markdown.lang.parser.MarkdownParserManager
import org.intellij.plugins.markdown.util.isFootnoteLabelText
import java.net.URI

internal class FootnoteMap private constructor(
  private val entries: Map<String, FootnoteEntry>,
  // Start offsets of code nodes that are multi-paragraph continuations of a footnote:
  //   [^note]: First paragraph.          ← PARAGRAPH (definition)
  //       This continuation is indented. ← CODE_BLOCK or CODE_FENCE sibling
  // These are consumed into the footnote body and suppressed from normal rendering.
  private val continuationBlocks: Set<Int>,
) {
  data class FootnoteEntry(val label: String, val number: Int, val bodyMarkdown: String)

  fun getEntry(label: String): FootnoteEntry? = entries[label]

  fun isContinuationBlock(startOffset: Int): Boolean = startOffset in continuationBlocks

  fun generateFootnoteHtml(baseUri: URI? = null): String {
    val sorted = entries.values.sortedBy { it.number }
    if (sorted.isEmpty()) return ""
    return buildString {
      append("<div class=\"footnotes\">\n")
      append("<ol>\n")
      for (entry in sorted) {
        ProgressManager.checkCanceled()
        append("<li id=\"fn:${entry.label}\">\n")
        val bodyHtml = renderBodyMarkdown(entry.bodyMarkdown, baseUri, this@FootnoteMap)
        append(insertBackLink(bodyHtml, entry.label))
        append("\n</li>\n")
      }
      append("</ol>\n")
      append("</div>")
    }
  }

  companion object {
    private fun insertBackLink(html: String, label: String): String {
      val backLink = "&nbsp;<a href=\"#fnref:$label\" class=\"reversefootnote\">&#x21A9;</a>"
      val lastP = html.lastIndexOf("</p>")
      return if (lastP >= 0) {
        html.substring(0, lastP) + backLink + html.substring(lastP)
      }
      else {
        "$html<p>$backLink</p>"
      }
    }

    private fun renderBodyMarkdown(body: String, baseUri: URI?, fullMap: FootnoteMap? = null): String {
      val content = if (body.endsWith('\n')) body else "$body\n"
      val parsedTree = MarkdownParserManager.createMarkdownParser(MarkdownParserManager.FLAVOUR)
        .buildMarkdownTreeFromString(CancellableText.of(content))
      val linkMap = LinkMap.buildLinkMap(parsedTree, content)
      val providers = MarkdownParserManager.FLAVOUR.createHtmlGeneratingProviders(linkMap, baseUri).toMutableMap()
      if (fullMap != null) {
        // Build a local map for suppression: identifies which PARAGRAPH, CODE_BLOCK, and CODE_FENCE nodes in this
        // body are footnote definitions/continuations that should be hidden (they go into the list).
        val localMap = build(parsedTree, content)
        providers[MarkdownElementTypes.PARAGRAPH] = FootnoteNodeSuppressor(localMap, providers[MarkdownElementTypes.PARAGRAPH]!!)
        providers[MarkdownElementTypes.CODE_BLOCK] = FootnoteNodeSuppressor(localMap, providers[MarkdownElementTypes.CODE_BLOCK]!!)
        providers[MarkdownElementTypes.CODE_FENCE] = FootnoteNodeSuppressor(localMap, providers[MarkdownElementTypes.CODE_FENCE]!!)
        // Render footnote references using the full map so numbers are correct across all levels.
        providers[MarkdownElementTypes.SHORT_REFERENCE_LINK] = FootnoteReferenceProvider(fullMap, providers[MarkdownElementTypes.SHORT_REFERENCE_LINK]!!)
        providers[MarkdownElementTypes.FULL_REFERENCE_LINK] = FootnoteReferenceProvider(fullMap, providers[MarkdownElementTypes.FULL_REFERENCE_LINK]!!)
      }
      return HtmlGenerator(content, parsedTree, providers, false).generateHtml()
        .removePrefix("<body>").removeSuffix("</body>").trim()
    }

    private fun extractRawParagraphContent(paragraphNode: ASTNode, text: String): String {
      // Drop only through the definition label and its colon, then trim leading whitespace/EOL.
      // This preserves a body that starts with a defined reference (e.g., "[^a]: [^b]").
      val contentNodes = paragraphNode.children
        .dropWhile { it.type != MarkdownTokenTypes.COLON }
        .drop(1)  // Skip the colon itself
        .dropWhile { it.type == MarkdownTokenTypes.WHITE_SPACE || it.type == MarkdownTokenTypes.EOL }
      val body = contentNodes.joinToString("") { it.getTextInNode(text).toString() }.trim()
      return body
    }

    private fun extractRawContinuationBlock(codeBlockNode: ASTNode, text: String): String {
      val blockText = codeBlockNode.getTextInNode(text).toString()
      val containerPrefix = getContainerPrefix(codeBlockNode, text)
      val lines = blockText.lines().map { stripContainerPrefix(it, containerPrefix) }
      val indentationColumns = if (codeBlockNode.type == MarkdownElementTypes.CODE_FENCE) {
        lines.firstOrNull()?.let { MarkdownCodeFenceUtils.getIndentationInfo(it).columns } ?: 0
      }
      else {
        lines.asSequence()
          .filter { it.isNotBlank() }
          .minOfOrNull { MarkdownCodeFenceUtils.getIndentationInfo(it).columns } ?: 0
      }
      val groups = mutableListOf<MutableList<String>>()
      var currentGroup = mutableListOf<String>()
      var hasBlank = false
      for (line in lines) {
        val stripped = stripIndentation(line, indentationColumns)
        if (stripped.isBlank()) {
          if (currentGroup.isNotEmpty()) hasBlank = true
        } else {
          if (hasBlank) {
            groups.add(currentGroup)
            currentGroup = mutableListOf()
            hasBlank = false
          }
          currentGroup.add(stripped)
        }
      }
      if (currentGroup.isNotEmpty()) groups.add(currentGroup)
      return groups.joinToString("\n\n") { it.joinToString("\n") }
    }

    private fun getContainerPrefix(node: ASTNode, text: String): String {
      val lineStart = text.lastIndexOf('\n', node.startOffset - 1) + 1
      return text.substring(lineStart, node.startOffset)
    }

    private fun stripContainerPrefix(line: String, prefix: String): String {
      if (prefix.isEmpty()) return line
      if (line.startsWith(prefix)) return line.removePrefix(prefix)
      val prefixWithoutTrailingSpace = prefix.trimEnd()
      return if (prefixWithoutTrailingSpace.isNotEmpty() && line.startsWith(prefixWithoutTrailingSpace)) {
        line.removePrefix(prefixWithoutTrailingSpace)
      }
      else {
        line
      }
    }

    private fun stripIndentation(line: String, columnsToStrip: Int): String {
      val offset = MarkdownCodeFenceUtils.getIndentationInfo(line, columnsToStrip).length
      return line.substring(offset)
    }

    private fun extractDefinitionLabel(node: ASTNode, text: String): String? {
      return when (node.type) {
        MarkdownElementTypes.PARAGRAPH -> {
          val firstChild = node.children.firstOrNull { it.type != MarkdownTokenTypes.WHITE_SPACE && it.type != MarkdownTokenTypes.EOL }
                           ?: return null
          val linkLabel = when (firstChild.type) {
            MarkdownElementTypes.SHORT_REFERENCE_LINK -> firstChild.findChildOfType(MarkdownElementTypes.LINK_LABEL)
            MarkdownElementTypes.LINK_LABEL -> firstChild
            else -> return null
          }
          // A bare reference like "[^note]" has no trailing colon: keep it a reference, not a definition.
          if (firstChild.endOffset >= text.length || text[firstChild.endOffset] != ':') return null
          linkLabel?.let { extractFootnoteLabel(it, text) }
        }
        MarkdownElementTypes.LINK_DEFINITION -> {
          val linkLabel = node.findChildOfType(MarkdownElementTypes.LINK_LABEL) ?: return null
          extractFootnoteLabel(linkLabel, text)
        }
        else -> null
      }
    }

    private fun extractDefinitionBody(node: ASTNode, text: String): String =
      when (node.type) {
        MarkdownElementTypes.PARAGRAPH -> extractRawParagraphContent(node, text)
        MarkdownElementTypes.LINK_DEFINITION ->
          node.findChildOfType(MarkdownElementTypes.LINK_DESTINATION)
              ?.getTextInNode(text)?.toString()?.trim() ?: ""
        else -> ""
      }

    private fun collectFromAst(
      tree: ASTNode,
      text: String,
      definitions: MutableMap<String, String>,
      continuationBlocks: MutableSet<Int>,
      referenceOrder: MutableList<String>,
    ) {
      tree.accept(object : RecursiveVisitor() {
        override fun visitNode(node: ASTNode) {
          ProgressManager.checkCanceled()
          val label = extractDefinitionLabel(node, text)
          if (label != null) {
            val siblings = node.parent?.children.orEmpty()
            val continuationNodes = siblings.drop(siblings.indexOf(node) + 1)
              .takeWhile {
                when {
                  it.children.isEmpty() -> true
                  it.type == MarkdownElementTypes.CODE_BLOCK || it.type == MarkdownElementTypes.CODE_FENCE ->
                    isIndentedContinuation(node, it, text)
                  else -> false
                }
              }
              .filter { it.type == MarkdownElementTypes.CODE_BLOCK || it.type == MarkdownElementTypes.CODE_FENCE }
            val bodyMarkdown = buildString {
              append(extractDefinitionBody(node, text))
              for (block in continuationNodes) {
                continuationBlocks.add(block.startOffset)
                append("\n\n")
                append(extractRawContinuationBlock(block, text))
              }
            }
            definitions[label] = bodyMarkdown
          }
          when (node.type) {
            MarkdownElementTypes.SHORT_REFERENCE_LINK -> {
              if (node.endOffset < text.length && text[node.endOffset] != ':') {
                val refLabel = extractFootnoteLabelFromNode(node, text)
                if (refLabel != null && refLabel !in referenceOrder) referenceOrder.add(refLabel)
              }
            }
            MarkdownElementTypes.FULL_REFERENCE_LINK -> {
              if (node.endOffset < text.length && text[node.endOffset] != ':') {
                // For [^note1][^note2], LINK_TEXT [^note1] also contains a footnote reference
                val linkText = node.findChildOfType(MarkdownElementTypes.LINK_TEXT)
                val textLabel = linkText?.let { extractFootnoteLabel(it, text) }
                if (textLabel != null && textLabel !in referenceOrder) referenceOrder.add(textLabel)
                val refLabel = extractFootnoteLabelFromNode(node, text)
                if (refLabel != null && refLabel !in referenceOrder) referenceOrder.add(refLabel)
              }
            }
          }
          super.visitNode(node)
        }
      })
      // Text-based fallback: find [^label] patterns not detected via AST parsing.
      // This handles cases where [^label]: body has brackets in the URL part (e.g.
      // [^a]: See[^b]), preventing the parser from creating a link definition and
      // adding [^a] to the link map, so [^a] isn't parsed as SHORT_REFERENCE_LINK.
      for (match in FOOTNOTE_REF_REGEX.findAll(text)) {
        val label = match.groupValues[1]
        val endOffset = match.range.last + 1
        if (endOffset < text.length && text[endOffset] == ':') continue  // skip definitions
        if (label in definitions && label !in referenceOrder) referenceOrder.add(label)
      }
    }

    private fun isIndentedContinuation(definition: ASTNode, block: ASTNode, text: String): Boolean {
      return leadingIndentation(definition, text) < leadingIndentation(block, text)
    }

    private fun leadingIndentation(node: ASTNode, text: String): Int {
      val startOffset = when (node.type) {
        MarkdownElementTypes.CODE_FENCE -> node.children.firstOrNull { it.type == MarkdownTokenTypes.CODE_FENCE_START }?.let {
          it.startOffset + MarkdownCodeFenceUtils.getIndentationInfo(it.getTextInNode(text)).length
        }
        MarkdownElementTypes.PARAGRAPH -> node.children.firstOrNull { it.type != MarkdownTokenTypes.WHITE_SPACE && it.type != MarkdownTokenTypes.EOL }?.startOffset
        else -> null
      } ?: node.startOffset
      val lineStart = text.lastIndexOf('\n', startOffset - 1) + 1
      if (node.type == MarkdownElementTypes.CODE_BLOCK) {
        val lineEnd = text.indexOf('\n', startOffset).takeUnless { it == -1 } ?: text.length
        return MarkdownCodeFenceUtils.getIndentationInfo(text.subSequence(startOffset, lineEnd)).columns
      }
      // Count every character up to the node, including a container marker (list bullet,
      // blockquote '>') on the same line. Continuation lines carry only whitespace there,
      // so this puts the definition and its sibling blocks on the same column baseline.
      return columnPosition(text, lineStart, startOffset)
    }

    private fun columnPosition(text: String, lineStart: Int, offset: Int): Int {
      var columns = 0
      for (index in lineStart until offset) {
        columns += if (text[index] == '\t') 4 - columns % 4 else 1
      }
      return columns
    }

    @Suppress("RegExpRedundantEscape")
    private val FOOTNOTE_REF_REGEX = Regex("""\[\^([^\]\n]+)]""")

    fun build(tree: ASTNode, text: String): FootnoteMap {
      val definitions = mutableMapOf<String, String>()
      val continuationBlocks = mutableSetOf<Int>()
      val referenceOrder = mutableListOf<String>()

      collectFromAst(tree, text, definitions, continuationBlocks, referenceOrder)

      // Recursively collect footnote definitions and references nested inside footnote bodies.
      // Footnotes defined inside another footnote's body go into the shared footnote list,
      // numbered in the order their references first appear (depth-first).
      val processed = mutableSetOf<String>()
      var i = 0
      while (i < referenceOrder.size) {
        ProgressManager.checkCanceled()
        val label = referenceOrder[i]
        if (label !in processed && label in definitions) {
          processed.add(label)
          val body = definitions[label]!!
          val content = if (body.endsWith('\n')) body else "$body\n"
          val bodyTree = MarkdownParserManager.createMarkdownParser(MarkdownParserManager.FLAVOUR)
            .buildMarkdownTreeFromString(CancellableText.of(content))
          val nestedDefs = mutableMapOf<String, String>()
          val nestedRefs = mutableListOf<String>()
          collectFromAst(bodyTree, content, nestedDefs, mutableSetOf(), nestedRefs)
          for ((defLabel, defBody) in nestedDefs) {
            if (defLabel !in definitions) definitions[defLabel] = defBody
          }
          // Insert nested references right after the current label so they're numbered in DFS order.
          var insertPos = i + 1
          for (refLabel in nestedRefs) {
            if (refLabel !in referenceOrder) {
              referenceOrder.add(insertPos++, refLabel)
            }
          }
        }
        i++
      }

      var number = 1
      val entries = mutableMapOf<String, FootnoteEntry>()
      for (label in referenceOrder) {
        val bodyMarkdown = definitions[label] ?: continue
        entries[label] = FootnoteEntry(label, number++, bodyMarkdown)
      }
      return FootnoteMap(entries, continuationBlocks)
    }

    internal fun isDefinition(node: ASTNode, text: String) = extractDefinitionLabel(node, text) != null

    internal fun extractFootnoteLabel(linkLabel: ASTNode, text: String): String? {
      val labelText = linkLabel.getTextInNode(text).toString()
      if (!isFootnoteLabelText(labelText)) return null
      val label = labelText.substring(2, labelText.length - 1)
      return label.ifBlank { null }
    }

    private fun extractFootnoteLabelFromNode(node: ASTNode, text: String): String? {
      val linkLabel = node.findChildOfType(MarkdownElementTypes.LINK_LABEL) ?: return null
      return extractFootnoteLabel(linkLabel, text)
    }
  }
}

internal class FootnoteNodeSuppressor(
  private val footnoteMap: FootnoteMap,
  private val fallback: GeneratingProvider,
) : GeneratingProvider {
  override fun processNode(visitor: HtmlGenerator.HtmlGeneratingVisitor, text: String, node: ASTNode) {
    if (FootnoteMap.isDefinition(node, text)) return
    if (footnoteMap.isContinuationBlock(node.startOffset)) return
    fallback.processNode(visitor, text, node)
  }
}

internal class FootnoteReferenceProvider(
  private val footnoteMap: FootnoteMap,
  private val fallback: GeneratingProvider,
) : GeneratingProvider {
  override fun processNode(visitor: HtmlGenerator.HtmlGeneratingVisitor, text: String, node: ASTNode) {
    val linkLabel = node.findChildOfType(MarkdownElementTypes.LINK_LABEL)
    val footnoteLabel = linkLabel?.let { FootnoteMap.extractFootnoteLabel(it, text) }

    if (footnoteLabel == null) {
      fallback.processNode(visitor, text, node)
      return
    }

    // For [^note1][^note2]: the parser produces FULL_REFERENCE_LINK(LINK_TEXT=[^note1], LINK_LABEL=[^note2]).
    // LINK_TEXT here is itself a footnote reference, so render it first.
    val linkText = node.findChildOfType(MarkdownElementTypes.LINK_TEXT)
    val textLabel = linkText?.let { FootnoteMap.extractFootnoteLabel(it, text) }
    if (textLabel != null) {
      val textEntry = footnoteMap.getEntry(textLabel)
      if (textEntry != null) {
        visitor.consumeHtml("<sup id=\"fnref:${textEntry.label}\"><a href=\"#fn:${textEntry.label}\">[${textEntry.number}]</a></sup>")
      } else {
        visitor.consumeHtml("[^$textLabel]")
      }
    }

    val entry = footnoteMap.getEntry(footnoteLabel)
    if (entry == null) {
      visitor.consumeHtml("[^$footnoteLabel]")
      return
    }

    visitor.consumeHtml(
      "<sup id=\"fnref:${entry.label}\"><a href=\"#fn:${entry.label}\">[${entry.number}]</a></sup>"
    )
  }
}
