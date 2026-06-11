// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections

import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.ui.ColorUtil
import com.intellij.util.ui.NamedColorUtil
import com.intellij.util.ui.UIUtil
import com.jetbrains.python.PyNames
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.documentation.PythonDocumentationProvider
import com.jetbrains.python.psi.PyCallable
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyKeywordArgument
import com.jetbrains.python.psi.types.PyCallableParameter
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.TypeEvalContext
import org.jetbrains.annotations.Nls

/**
 * Shared logic and messaging for "this call matches none of the candidate signatures" inspection reports,
 * used by both [PyTypeCheckerInspection] (argument type mismatches) and [PyArgumentListInspection]
 * (unexpected/unfilled arguments). Both inspections describe the failure with the same model and render it
 * the same way:
 *
 * ```
 * No overload of `f` matches the arguments         (header; "No signature matches the arguments" if candidates differ)
 * Argument types:  (int, c=str)                     ← provided argument types; unmatched ones stand out
 * Expected one of: (a: int, b: int)                 ← one row per candidate; unmatched parameters stand out
 *                  (a: int, option: None)
 * ```
 *
 * The parts that match are muted; only the offending parts are emphasized. The plain-text description (for
 * the Problems view and batch mode) carries the same content without styling.
 */
internal object PyMismatchTooltips {
  /** One rendered cell — a provided argument type or an expected parameter — and whether it matched. */
  @JvmRecord
  data class Slot(@NlsSafe val text: String, val matched: Boolean)

  /**
   * The header naming the single common callee (rendered with the name as a `<code>` span in the tooltip and
   * in single quotes in the description), or a generic "no signature" message when the candidates differ or
   * are anonymous. A `__init__`/`__new__` callee is named after its class, since the dunder name carries no
   * information for the reader.
   */
  @JvmStatic
  fun header(callables: List<PyCallable?>): PyInspectionMessages.ProblemMessage {
    val names = callables.map { calleeDisplayName(it) }
    val common = names.firstOrNull()
    return if (!common.isNullOrBlank() && names.all { it == common })
      PyPsiBundle.problemMessage("INSP.type.checker.no.overload.matches.arguments", common)
    else
      PyPsiBundle.problemMessage("INSP.type.checker.no.signature.matches.arguments")
  }

  private fun calleeDisplayName(callable: PyCallable?): String? {
    val name = callable?.name ?: return null
    if (name == PyNames.INIT || name == PyNames.NEW) {
      (callable as? PyFunction)?.containingClass?.name?.let { return it }
    }
    return name
  }

  /** Flat one-line description for the Problems view and batch mode. */
  @JvmStatic
  @InspectionMessage
  fun description(
    header: PyInspectionMessages.ProblemMessage,
    argumentSlots: List<Slot>,
    expectedRows: List<List<Slot>>,
  ): @InspectionMessage String {
    val expected = expectedRows.joinToString(", ") { tupleText(it) }
    return header.description + ". " +
           PyPsiBundle.message("INSP.type.checker.argument.types.label") + " " + tupleText(argumentSlots) + ". " +
           PyPsiBundle.message("INSP.type.checker.expected.one.of.label") + " " + expected
  }

  /** Styled HTML tooltip for the editor hover. */
  @JvmStatic
  @NlsContexts.Tooltip
  fun tooltip(
    header: PyInspectionMessages.ProblemMessage,
    argumentSlots: List<Slot>,
    expectedRows: List<List<Slot>>,
  ): @NlsContexts.Tooltip String {
    val rows = mutableListOf(
      row(PyPsiBundle.message("INSP.type.checker.argument.types.label"), codeTuple(argumentSlots.map { argumentChunk(it) }))
    )
    expectedRows.forEachIndexed { i, slots ->
      val label = if (i == 0) PyPsiBundle.message("INSP.type.checker.expected.one.of.label") else ""
      rows.add(row(label, codeTuple(slots.map { expectedChunk(it) })))
    }
    val headerHtml = header.tooltip.removeSurrounding("<html>", "</html>")
    return HtmlBuilder()
      .append(HtmlChunk.raw(headerHtml))
      .append(HtmlBuilder().also { table -> rows.forEach(table::append) }.wrapWith("table"))
      .wrapWith("html")
      .toString()
  }

  /** Renders a provided argument as `type` or `keyword=type`. */
  @JvmStatic
  @NlsSafe
  fun actualArgumentText(argument: PyExpression, type: PyType?, context: TypeEvalContext): @NlsSafe String {
    val typeName = PythonDocumentationProvider.getTypeName(type, context)
    if (argument is PyKeywordArgument) {
      argument.keyword?.let { return "$it=$typeName" }
    }
    return typeName
  }

  /** Renders an expected parameter as `name: type` (with `*`/`**` for containers, just `name` when untyped). */
  @JvmStatic
  @NlsSafe
  fun parameterText(parameter: PyCallableParameter, context: TypeEvalContext): @NlsSafe String {
    val type = parameter.getType(context)
    val typeName = if (type == null) null else PythonDocumentationProvider.getTypeName(type, context)
    val name = parameter.name ?: return typeName.orEmpty()
    val prefix = if (parameter.isPositionalContainer) "*" else if (parameter.isKeywordContainer) "**" else ""
    return if (typeName == null) "$prefix$name" else "$prefix$name: $typeName"
  }

  private val mutedStyle: String get() = "color: " + ColorUtil.toHtmlColor(UIUtil.getContextHelpForeground()) + ";"
  private val errorStyle: String get() = "color: " + ColorUtil.toHtmlColor(NamedColorUtil.getErrorForeground()) + ";"
  private val labelStyle: String get() = "$mutedStyle padding: 0px 8px 0px 4px;"

  /** A provided-argument cell: muted when it maps somewhere, error-bold when it matches no candidate. */
  private fun argumentChunk(slot: Slot): HtmlChunk {
    val text = HtmlChunk.text(slot.text)
    return if (slot.matched) text.wrapWith(HtmlChunk.span().style(mutedStyle))
    else text.bold().wrapWith(HtmlChunk.span().style(errorStyle))
  }

  /** An expected-parameter cell: muted when a matching argument was provided, bold when it was not. */
  private fun expectedChunk(slot: Slot): HtmlChunk {
    val text = HtmlChunk.text(slot.text)
    return if (slot.matched) text.wrapWith(HtmlChunk.span().style(mutedStyle)) else text.bold()
  }

  private fun row(@Nls label: String, value: HtmlChunk): HtmlChunk = HtmlChunk.tag("tr").children(
    HtmlChunk.tag("td").style(labelStyle).addText(label),
    HtmlChunk.tag("td").child(value),
  )

  private fun codeTuple(cells: List<HtmlChunk>): HtmlChunk {
    val builder = HtmlBuilder()
    builder.append(HtmlChunk.span().style(mutedStyle).addText("("))
    for ((i, cell) in cells.withIndex()) {
      if (i > 0) builder.append(HtmlChunk.span().style(mutedStyle).addText(", "))
      builder.append(cell)
    }
    builder.append(HtmlChunk.span().style(mutedStyle).addText(")"))
    return builder.wrapWith("code")
  }

  private fun tupleText(slots: List<Slot>): String = slots.joinToString(", ", "(", ")") { it.text }
}
