// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.fstrings

import com.intellij.lang.documentation.DocumentationMarkup
import com.intellij.model.Pointer
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.platform.backend.documentation.DocumentationResult
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.DocumentationTargetProvider
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parentOfType
import com.jetbrains.python.PyElementTypes
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.PythonLanguage
import com.jetbrains.python.psi.PyFStringFragment
import com.jetbrains.python.psi.PyFStringFragmentFormatPart
import org.jetbrains.annotations.Nls

// The example is rendered eagerly from the width and the precision in the document, so both need a
// bound. Without one, a spec such as ".999999999f" makes the popup allocate a string of that size.
private const val MAX_EXAMPLE_WIDTH = 60
private const val MAX_EXAMPLE_PRECISION = 20

/**
 * Provides hover documentation for an f-string format specification.
 *
 * Hovering anywhere after the colon describes the whole specification, not the single character under the
 * caret. The spec is read with the shared [PyFormatSpec] parser.
 */
class PyFStringFormatSpecHoverDocumentationProvider : DocumentationTargetProvider {

  override fun documentationTargets(file: PsiFile, offset: Int): List<DocumentationTarget> {
    if (!file.language.isKindOf(PythonLanguage.getInstance())) {
      return emptyList()
    }

    val element = file.findElementAt(offset) ?: return emptyList()

    val formatPart = element.parentOfType<PyFStringFragmentFormatPart>(withSelf = true) ?: return emptyList()

    // Bail out if the caret is inside a nested replacement field (e.g. the `width` in f"{x:{width}.2f}"):
    // that expression has its own documentation and must not be shadowed by format-spec docs.
    val enclosingFragment = element.parentOfType<PyFStringFragment>(withSelf = true)
    if (enclosingFragment != null && PsiTreeUtil.isAncestor(formatPart, enclosingFragment, false)) {
      return emptyList()
    }

    // Only describe the spec when the caret sits after the colon.
    val formatStartOffset = findFormatStartOffset(formatPart) ?: return emptyList()
    if (offset < formatStartOffset) {
      return emptyList()
    }

    val formatText = getFormatSpecText(formatPart) ?: return emptyList()
    if (formatText.isEmpty()) return emptyList()

    return listOf(PyFormatSpecDocumentationTarget(PyFormatSpec.parse(formatText)))
  }

  private fun findFormatStartOffset(formatPart: PyFStringFragmentFormatPart): Int? {
    for (child in formatPart.node.getChildren(null)) {
      if (child.elementType == PyTokenTypes.FSTRING_FRAGMENT_FORMAT_START) {
        return child.startOffset + child.textLength
      }
    }
    return null
  }

  private fun getFormatSpecText(formatPart: PyFStringFragmentFormatPart): String? {
    var foundFormatStart = false
    val text = StringBuilder()

    for (child in formatPart.node.getChildren(null)) {
      if (child.elementType == PyTokenTypes.FSTRING_FRAGMENT_FORMAT_START) {
        foundFormatStart = true
        continue
      }
      // Skip nested replacement fields ({...}) and the closing brace: only literal spec text is parsed.
      if (foundFormatStart &&
          child.elementType != PyTokenTypes.FSTRING_FRAGMENT_END &&
          child.elementType != PyElementTypes.FSTRING_FRAGMENT) {
        text.append(child.text)
      }
    }

    return if (foundFormatStart) text.toString() else null
  }
}

/**
 * Documentation target for a whole format specification.
 *
 * The markup uses the platform's documentation sections, so the popup styles it like every other quick
 * documentation and it follows the editor theme.
 */
internal class PyFormatSpecDocumentationTarget(private val spec: PyFormatSpec) : DocumentationTarget {

  override fun computePresentation(): TargetPresentation =
    TargetPresentation.builder(title()).presentation()

  override fun createPointer(): Pointer<out DocumentationTarget> = Pointer.hardPointer(this)

  private fun title(): @Nls String = PyPsiBundle.message("fstring.format.spec.doc.title.for", spec.text)

  override fun computeDocumentation(): DocumentationResult {
    // Every dynamic value goes in through HtmlChunk, which escapes it. A spec may hold '<', '>' or '&'
    // (an alignment operator or a fill character), so an unescaped value would corrupt the markup.
    val builder = HtmlBuilder()
      .append(DocumentationMarkup.DEFINITION_ELEMENT.child(
        DocumentationMarkup.PRE_ELEMENT
          .addText(PyPsiBundle.message("fstring.format.spec.doc.title") + ": ")
          .child(code(spec.text))
      ))

    if (spec.isDatetime) {
      builder.append(DocumentationMarkup.CONTENT_ELEMENT.child(
        HtmlChunk.p().addText(PyPsiBundle.message("fstring.format.spec.doc.datetime"))
      ))
      builder.append(sections(spec.datetimeDirectives.mapNotNull { directive ->
        val option = PyFormatSpecCatalog[directive] ?: return@mapNotNull null
        section(code(directive), HtmlChunk.text("${option.shortDescription} - ${option.fullDescription}"))
      }))
      return DocumentationResult.documentation(builder.toString())
    }

    val rows = componentSections()
    if (rows.isEmpty()) {
      builder.append(DocumentationMarkup.CONTENT_ELEMENT.child(
        HtmlChunk.p().child(HtmlChunk.text(PyPsiBundle.message("fstring.format.spec.doc.no.components")).italic())
      ))
      return DocumentationResult.documentation(builder.toString())
    }

    builder.append(sections(rows))
    generateExample()?.let { example ->
      builder.append(DocumentationMarkup.CONTENT_ELEMENT.child(
        HtmlChunk.p()
          .child(HtmlChunk.text(PyPsiBundle.message("fstring.format.spec.doc.example")).bold())
          .addText(" ")
          .child(code(example))
      ))
    }
    return DocumentationResult.documentation(builder.toString())
  }

  /** One row per recognized component, in the order the spec states them. */
  private fun componentSections(): List<HtmlChunk> {
    val rows = mutableListOf<HtmlChunk>()

    spec.fill?.let {
      rows.add(section(HtmlChunk.text(PyPsiBundle.message("fstring.format.spec.doc.component.fill")),
                       value(it), PyPsiBundle.message("fstring.format.spec.doc.component.fill.meaning")))
    }
    spec.align?.let {
      rows.add(section(HtmlChunk.text(PyPsiBundle.message("fstring.format.spec.doc.component.alignment")),
                       code(it.toString()), meaningOf(it)))
    }
    spec.sign?.let {
      rows.add(section(HtmlChunk.text(PyPsiBundle.message("fstring.format.spec.doc.component.sign")),
                       value(it), meaningOf(it)))
    }
    if (spec.hasCoerceNegativeZero) {
      rows.add(section(HtmlChunk.text(PyPsiBundle.message("fstring.format.spec.doc.component.coerce")),
                       code("z"), PyPsiBundle.message("fstring.format.spec.doc.component.coerce.meaning")))
    }
    if (spec.hasAlternateForm) {
      rows.add(section(HtmlChunk.text(PyPsiBundle.message("fstring.format.spec.doc.component.alternate")),
                       code("#"), PyPsiBundle.message("fstring.format.spec.doc.component.alternate.meaning")))
    }
    if (spec.hasZeroPadding) {
      rows.add(section(HtmlChunk.text(PyPsiBundle.message("fstring.format.spec.doc.component.zero.padding")),
                       code("0"), PyPsiBundle.message("fstring.format.spec.doc.component.zero.padding.meaning")))
    }
    spec.width?.let {
      rows.add(section(HtmlChunk.text(PyPsiBundle.message("fstring.format.spec.doc.component.width")),
                       code(it), PyPsiBundle.message("fstring.format.spec.doc.component.width.meaning")))
    }
    spec.grouping?.let {
      rows.add(section(HtmlChunk.text(PyPsiBundle.message("fstring.format.spec.doc.component.grouping")),
                       code(it.toString()), meaningOf(it)))
    }
    spec.precision?.let {
      rows.add(section(HtmlChunk.text(PyPsiBundle.message("fstring.format.spec.doc.component.precision")),
                       code(".$it"), PyPsiBundle.message("fstring.format.spec.doc.component.precision.meaning")))
    }
    spec.type?.let {
      rows.add(section(HtmlChunk.text(PyPsiBundle.message("fstring.format.spec.doc.component.type")),
                       code(it.toString()), meaningOf(it)))
    }

    return rows
  }

  private fun sections(rows: List<HtmlChunk>): HtmlChunk =
    DocumentationMarkup.SECTIONS_TABLE.children(rows)

  private fun section(header: HtmlChunk, content: HtmlChunk): HtmlChunk =
    HtmlChunk.tag("tr").children(
      DocumentationMarkup.SECTION_HEADER_CELL.child(header),
      DocumentationMarkup.SECTION_CONTENT_CELL.child(content),
    )

  private fun section(header: HtmlChunk, value: HtmlChunk, meaning: @Nls String): HtmlChunk =
    section(header, HtmlBuilder().append(value).append(HtmlChunk.text(" — $meaning")).toFragment())

  private fun code(text: String): HtmlChunk = HtmlChunk.tag("code").addText(text)

  /** A space is invisible inside `<code>`, so it is named instead of shown. */
  private fun value(char: Char): HtmlChunk =
    if (char == ' ') HtmlChunk.text(PyPsiBundle.message("fstring.format.spec.doc.value.space")) else code(char.toString())

  /** Short, human-readable meaning of a single-character option, from the shared catalog. */
  private fun meaningOf(spec: Char): @Nls String =
    PyFormatSpecCatalog[spec.toString()]?.shortDescription
    ?: PyPsiBundle.message("fstring.format.spec.doc.component.unknown")

  // The example formatter is best-effort: if it cannot render a sample, no example is shown.
  private fun generateExample(): String? {
    // A width or a precision beyond the bound gets no example, rather than a huge one.
    if ((spec.width?.toIntOrNull() ?: 0) > MAX_EXAMPLE_WIDTH) return null
    if ((spec.precision?.toIntOrNull() ?: 0) > MAX_EXAMPLE_PRECISION) return null

    val type = spec.type
    val isFloatType = type in listOf('f', 'F', 'e', 'E', 'g', 'G', '%')
    val isIntType = type in listOf('d', 'b', 'o', 'x', 'X', 'c', 'n')

    // Pick a sample value that demonstrates the format well
    val sampleValue: Any = when {
      type == 'c' -> 65                    // a printable codepoint ('A')
      isFloatType -> 1234.5678
      isIntType -> 1000
      type == 's' -> "text"
      spec.precision != null -> 3.14159    // Has precision, likely float
      spec.grouping != null -> 1000000     // Has grouping, likely large number
      else -> 42  // Default to simple int
    }

    return try {
      formatValue(sampleValue)
    }
    catch (_: IllegalArgumentException) {
      // e.g. an invalid String.format pattern for an unusual spec
      null
    }
  }

  private fun formatValue(value: Any): String {
    val width = spec.width?.toIntOrNull() ?: 0
    val precision = spec.precision?.toIntOrNull() ?: 6
    val fill = spec.fill ?: ' '
    val align = spec.align ?: '>'
    val type = spec.type

    // Format the core value first
    var result = when (type) {
      'd' -> (value as? Number)?.toLong()?.toString() ?: value.toString()
      'f', 'F' -> String.format("%.${precision}f", (value as Number).toDouble())
      'e' -> String.format("%.${precision}e", (value as Number).toDouble())
      'E' -> String.format("%.${precision}E", (value as Number).toDouble())
      'g' -> String.format("%.${precision}g", (value as Number).toDouble())
      'G' -> String.format("%.${precision}G", (value as Number).toDouble())
      '%' -> String.format("%.${precision}f%%", (value as Number).toDouble() * 100)
      'b' -> java.lang.Long.toBinaryString((value as Number).toLong())
      'o' -> java.lang.Long.toOctalString((value as Number).toLong())
      'x' -> java.lang.Long.toHexString((value as Number).toLong())
      'X' -> java.lang.Long.toHexString((value as Number).toLong()).uppercase()
      'c' -> ((value as? Number)?.toInt() ?: 0).toChar().toString()
      'n' -> (value as? Number)?.toLong()?.toString() ?: value.toString()
      's' -> value.toString()
      else -> {
        // No type specified - format based on value type
        when (value) {
          is Double, is Float -> {
            if (spec.precision != null) String.format("%.${precision}f", (value as Number).toDouble())
            else value.toString()
          }
          else -> value.toString()
        }
      }
    }

    // Apply grouping. Integer presentation types b/o/x/X group every 4 digits; others every 3.
    spec.grouping?.let { grouping ->
      val groupSize = if (type in listOf('b', 'o', 'x', 'X')) 4 else 3
      result = applyGrouping(result, grouping, groupSize)
    }

    // Apply sign
    if (spec.sign != null && value is Number && value.toDouble() >= 0) {
      result = when (spec.sign) {
        '+' -> "+$result"
        ' ' -> " $result"
        else -> result
      }
    }

    // Apply alternate form
    if (spec.hasAlternateForm) {
      result = when (type) {
        'b' -> "0b$result"
        'o' -> "0o$result"
        'x' -> "0x$result"
        'X' -> "0X$result"
        else -> result
      }
    }

    // Apply width and alignment
    if (width > result.length) {
      val padding = width - result.length
      result = when (align) {
        '<' -> result + fill.toString().repeat(padding)
        '>' -> fill.toString().repeat(padding) + result
        '^' -> {
          val left = padding / 2
          val right = padding - left
          fill.toString().repeat(left) + result + fill.toString().repeat(right)
        }
        '=' -> {
          // Pad after sign
          val signPart = if (result.startsWith('+') || result.startsWith('-') || result.startsWith(' ')) result[0].toString() else ""
          val numPart = if (signPart.isNotEmpty()) result.substring(1) else result
          signPart + fill.toString().repeat(padding) + numPart
        }
        else -> fill.toString().repeat(padding) + result
      }
    }

    return result
  }

  private fun applyGrouping(value: String, groupingChar: Char, groupSize: Int): String {
    // Find the integer part (before decimal point)
    val parts = value.split('.')
    val intPart = parts[0].trimStart('-', '+', ' ')
    val prefix = value.takeWhile { it == '-' || it == '+' || it == ' ' }
    val decPart = if (parts.size > 1) ".${parts[1]}" else ""

    // Group digits from right to left
    val grouped = intPart.reversed().chunked(groupSize).joinToString(groupingChar.toString()).reversed()
    return prefix + grouped + decPart
  }
}
