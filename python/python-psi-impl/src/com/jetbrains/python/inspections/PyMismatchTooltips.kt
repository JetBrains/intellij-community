// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections

import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.HtmlChunk
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
import com.jetbrains.python.psi.types.isAnyOrUnknown
import com.jetbrains.python.psi.types.isUnknown
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
  /**
   * One provided argument or expected parameter, split into a [name] part (a `keyword=` for arguments, a
   * `name: ` for parameters, or empty) and a [type] part, plus whether it matched. Keeping the parts separate
   * lets the renderer right-align the names so the types line up column-by-column.
   */
  @JvmRecord
  data class Slot(@NlsSafe val name: String, @NlsSafe val type: String, val matched: Boolean) {
    @get:NlsSafe val text: String get() = name + type
  }

  /** A provided argument rendered as `type` or `keyword=type`. */
  @JvmStatic
  fun argumentSlot(argument: PyExpression, type: PyType?, context: TypeEvalContext, matched: Boolean): Slot {
    val typeName = PythonDocumentationProvider.getTypeName(type, context)
    val name = (argument as? PyKeywordArgument)?.keyword?.let { "$it=" } ?: ""
    return Slot(name, typeName, matched)
  }

  /** An expected parameter rendered as `name: type` (with `*`/`**` for containers, just `name` when untyped). */
  @JvmStatic
  fun parameterSlot(parameter: PyCallableParameter, context: TypeEvalContext, matched: Boolean): Slot {
    val type = parameter.getType(context)
    return parameterSlot(parameter, if (type.isUnknown) null else PythonDocumentationProvider.getTypeName(type, context), matched)
  }

  /**
   * An expected parameter rendered as `name: type` (with `*`/`**` for containers, just `name` when [typeName] is
   * `null`), using an already-rendered [typeName]. Use this when the caller has a different type to show than the
   * parameter's declared one (e.g. a substituted type).
   */
  @JvmStatic
  fun parameterSlot(parameter: PyCallableParameter, @NlsSafe typeName: String?, matched: Boolean): Slot {
    val prefix = containerPrefix(parameter)
    val name = parameter.name ?: return Slot("", typeName.orEmpty(), matched)
    return if (typeName == null) Slot("$prefix$name", "", matched) else Slot("$prefix$name: ", typeName, matched)
  }

  /** The `*`/`**` prefix for a positional/keyword container parameter, or empty for an ordinary parameter. */
  @JvmStatic
  @NlsSafe
  fun containerPrefix(parameter: PyCallableParameter): String =
    if (parameter.isPositionalContainer) "*" else if (parameter.isKeywordContainer) "**" else ""

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

  /**
   * Styled HTML tooltip for the editor hover. The provided argument types and each candidate signature are
   * rendered as aligned, code-styled rows (via [PyTypeDiffGrid]) so the arguments line up column-by-column with the
   * parameters; the parts that match are muted and only the offending parts stand out.
   */
  @JvmStatic
  @NlsContexts.Tooltip
  fun tooltip(
    header: PyInspectionMessages.ProblemMessage,
    argumentSlots: List<Slot>,
    expectedRows: List<List<Slot>>,
  ): @NlsContexts.Tooltip String {
    // When the structural diff is disabled, fall back to the plain description as the tooltip (no aligned grid).
    if (!PyTypeDiff.diffTooltipsEnabled()) {
      return HtmlChunk.text(description(header, argumentSlots, expectedRows)).wrapWith("html").toString()
    }
    val columnCount = (expectedRows + listOf(argumentSlots)).maxOf { it.size }
    val rows = mutableListOf(rowCells(argumentSlots, columnCount))
    expectedRows.forEach { rows.add(rowCells(it, columnCount)) }
    val labels = buildList {
      add(PyPsiBundle.message("INSP.type.checker.argument.types.label"))
      expectedRows.forEachIndexed { i, _ ->
        add(if (i == 0) PyPsiBundle.message("INSP.type.checker.expected.one.of.label") else "")
      }
    }
    @NlsSafe val headerHtml = header.tooltip.removeSurrounding("<html>", "</html>")
    return PyTypeDiffGrid.tooltip(HtmlChunk.raw(headerHtml), rows, labels)
  }

  /**
   * A `(slot, slot, …)` tuple rendered as [PyTypeDiffGrid] cells. Each slot becomes a right-aligned name cell and a
   * type cell (so the types line up); an unmatched slot's type is red, but its name is never highlighted.
   */
  private fun rowCells(slots: List<Slot>, columnCount: Int): List<PyTypeDiffGrid.Cell> {
    val cells = mutableListOf(PyTypeDiffGrid.delim("("))
    for (i in 0 until columnCount) {
      val slot = slots.getOrNull(i)
      val suffix = if (i < slots.lastIndex) ", " else ""
      if (slot == null) {
        cells.add(PyTypeDiffGrid.delim(""))
        cells.add(PyTypeDiffGrid.delim(""))
      }
      else {
        cells.add(PyTypeDiffGrid.value(slot.name, mismatch = false, alignRight = true))
        cells.add(PyTypeDiffGrid.value(slot.type, mismatch = !slot.matched, suffix = suffix))
      }
    }
    cells.add(PyTypeDiffGrid.delim(")"))
    return cells
  }

  private fun tupleText(slots: List<Slot>): String = slots.joinToString(", ", "(", ")") { it.text }
}
