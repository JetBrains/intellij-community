// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.devkit

import com.intellij.lang.documentation.DocumentationMarkup
import com.intellij.model.Pointer
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.platform.backend.documentation.DocumentationResult
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.DocumentationTargetProvider
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.psi.PsiFile
import com.intellij.python.testAssertions.PyTestAssertionToken
import com.intellij.python.testAssertions.PyTestDirective
import com.intellij.python.testAssertions.PyTestDirectives

/**
 * Shows the documentation of the assertion language of `PyCodeInsightTestCase` in an injected Python text.
 *
 * A directive name or a modifier shows its description from [PyTestDirectives]. A marker shows the code that it marks.
 */
internal class PyTestAssertionDocumentationTargetProvider : DocumentationTargetProvider {

  override fun documentationTargets(file: PsiFile, offset: Int): List<DocumentationTarget> {
    val testText = PyTestCaseText.of(file) ?: return emptyList()
    // The caret can also stand right after a word. A token that holds the caret comes first.
    val tokens = testText.parseResult.tokens
    val token = tokens.find { offset in it.start until it.end } ?: tokens.find { offset == it.end } ?: return emptyList()
    val target = when (token.kind) {
      PyTestAssertionToken.Kind.DIRECTIVE, PyTestAssertionToken.Kind.MODIFIER -> {
        PyTestDirectives.find(testText.text.substring(token.start, token.end))?.let(::DirectiveDocumentationTarget)
      }
      PyTestAssertionToken.Kind.MARKER -> markerTarget(testText, token)
    }
    return listOfNotNull(target)
  }

  private fun markerTarget(testText: PyTestCaseText, token: PyTestAssertionToken): DocumentationTarget? {
    val assertion = testText.assertionAt(token.start) ?: return null
    val codeRange = testText.codeRange(assertion) ?: return null
    return MarkerDocumentationTarget(assertion.codeLineStart + 1, codeRange.substring(testText.text))
  }

  private data class DirectiveDocumentationTarget(val directive: PyTestDirective) : DocumentationTarget {
    override fun createPointer(): Pointer<out DocumentationTarget> = Pointer.hardPointer(this)

    override fun computePresentation(): TargetPresentation = TargetPresentation.builder(directive.name).presentation()

    override fun computeDocumentation(): DocumentationResult {
      @NlsSafe val definition = "${directive.name} ${directive.payloadGrammar}".trim()
      @NlsSafe val description = directive.description
      val sections = buildList {
        if (directive.payloadGrammar.isNotEmpty()) {
          add(section(PyDevKitBundle.message("test.assertion.doc.payload"), HtmlChunk.tag("code").addText(directive.payloadGrammar)))
        }
        if (directive.values.isNotEmpty()) {
          val values = HtmlChunk.tag("code").addText(directive.values.joinToString(", "))
          add(section(PyDevKitBundle.message("test.assertion.doc.values"), values))
        }
      }
      val html = HtmlChunk.fragment(
        DocumentationMarkup.DEFINITION_ELEMENT.child(HtmlChunk.tag("pre").addText(definition)),
        DocumentationMarkup.CONTENT_ELEMENT.addText(description),
        if (sections.isEmpty()) HtmlChunk.empty() else DocumentationMarkup.SECTIONS_TABLE.children(sections),
      )
      return DocumentationResult.documentation(html.toString())
    }

    private fun section(title: String, content: HtmlChunk): HtmlChunk =
      HtmlChunk.tag("tr").children(DocumentationMarkup.SECTION_HEADER_CELL.addText(title),
                                   DocumentationMarkup.SECTION_CONTENT_CELL.child(content))
  }

  private data class MarkerDocumentationTarget(val codeLine: Int, @NlsSafe val markedText: String) : DocumentationTarget {
    override fun createPointer(): Pointer<out DocumentationTarget> = Pointer.hardPointer(this)

    override fun computePresentation(): TargetPresentation = TargetPresentation.builder(markedText).presentation()

    override fun computeDocumentation(): DocumentationResult {
      val html = DocumentationMarkup.CONTENT_ELEMENT.children(
        HtmlChunk.text(PyDevKitBundle.message("test.assertion.doc.marker", codeLine)),
        HtmlChunk.br(),
        HtmlChunk.tag("code").addText(markedText),
      )
      return DocumentationResult.documentation(html.toString())
    }
  }
}
