// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.fstrings

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.lang.documentation.DocumentationMarkup
import com.intellij.model.Pointer
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.platform.backend.documentation.DocumentationResult
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.LookupElementDocumentationTargetProvider
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.psi.PsiFile
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.PythonLanguage
import org.jetbrains.annotations.Nls

/**
 * Describes the f-string format spec option that a completion item stands for.
 *
 * The option travels on the lookup element under [FORMAT_SPEC_OPTION_KEY], so this provider never parses
 * the item text again.
 */
class PyFStringFormatSpecDocumentationProvider : LookupElementDocumentationTargetProvider {
  override fun documentationTarget(psiFile: PsiFile, element: LookupElement, offset: Int): DocumentationTarget? {
    if (!psiFile.language.isKindOf(PythonLanguage.getInstance())) {
      return null
    }

    val option = element.getUserData(FORMAT_SPEC_OPTION_KEY) ?: return null
    return PyFormatSpecOptionDocumentationTarget(option)
  }
}

/** Documentation target for a single option of the Python format mini-language. */
internal class PyFormatSpecOptionDocumentationTarget(
  private val option: PyFormatSpecOption,
) : DocumentationTarget {

  override fun createPointer(): Pointer<out DocumentationTarget> = Pointer.hardPointer(this)

  override fun computePresentation(): TargetPresentation =
    TargetPresentation.builder(option.spec).presentableText(option.spec).presentation()

  override fun computeDocumentationHint(): @Nls String =
    HtmlBuilder()
      .append(HtmlChunk.text(option.spec).bold())
      .append(HtmlChunk.text(" — "))
      .append(HtmlChunk.text(option.shortDescription))
      .toString()

  override fun computeDocumentation(): DocumentationResult {
    // Every dynamic value goes in through HtmlChunk.text, which escapes it. A spec may hold '<', '>' or
    // '&', so an unescaped value would corrupt the markup.
    val definition = DocumentationMarkup.DEFINITION_ELEMENT.child(
      DocumentationMarkup.PRE_ELEMENT.child(HtmlChunk.text(option.spec).bold())
    )

    val content = HtmlBuilder()
      .append(HtmlChunk.p().child(HtmlChunk.text(option.category.label).italic()))
      .append(HtmlChunk.p().child(HtmlChunk.text(option.shortDescription).bold()))
      .append(HtmlChunk.p().addText(option.fullDescription))

    option.example?.let { example ->
      content
        .append(HtmlChunk.p().child(HtmlChunk.text(PyPsiBundle.message("fstring.format.spec.doc.example")).bold()))
        .append(DocumentationMarkup.PRE_ELEMENT.child(HtmlChunk.tag("code").addText(example)))
    }

    val html = HtmlBuilder()
      .append(definition)
      .append(DocumentationMarkup.CONTENT_ELEMENT.child(content.toFragment()))
      .toString()
    return DocumentationResult.documentation(html)
  }
}
