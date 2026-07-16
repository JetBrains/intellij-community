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
import com.jetbrains.python.psi.types.isUnknown

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
   * One provided argument or expected parameter: a [name] part (a `keyword=` for arguments, a `name: ` for
   * parameters, or empty) followed by its type. The type is carried as a [PyType] — never just a rendered string —
   * so the grid can ALWAYS colour and link it; a slot that shows a type without one cannot be built. Construct
   * slots only through [argument]/[parameter]/[ofType] (the constructor is private), which derive the displayed
   * [type] name from the [PyType], so the name and the type can never diverge.
   */
  class Slot private constructor(
    @NlsSafe val name: String,
    val pyType: PyType?,
    @NlsSafe val type: String,
    val matched: Boolean,
  ) {
    /** The plain `name + type` text, for the Problems-view description. */
    @get:NlsSafe val text: String get() = name + type

    companion object {
      /** A provided argument rendered as `type` or `keyword=type`. An argument always has an (inferred) type, so it
       *  is always rendered — `getTypeName` shows `Any` when it can't be determined. */
      @JvmStatic
      fun argument(argument: PyExpression, type: PyType?, context: TypeEvalContext, matched: Boolean): Slot {
        val name = (argument as? PyKeywordArgument)?.keyword?.let { "$it=" } ?: ""
        return Slot(name, type, PythonDocumentationProvider.getTypeName(type, context), matched)
      }

      /** An expected parameter using its DECLARED type: an unannotated parameter (a null type) shows just its name,
       *  with no `: type`. */
      @JvmStatic
      fun parameter(parameter: PyCallableParameter, context: TypeEvalContext, matched: Boolean): Slot {
        val type = parameter.getType(context)
        return of(parameter, type, if (type.isUnknown) null else PythonDocumentationProvider.getTypeName(type, context), matched)
      }

      /** An expected parameter shown with a specific [type] (e.g. one substituted at the call site) rather than its
       *  declared one — always rendered (`getTypeName` shows `Any` when unknown). The [type] is REQUIRED, so its
       *  colour and link are never dropped. */
      @JvmStatic
      fun parameter(parameter: PyCallableParameter, type: PyType?, context: TypeEvalContext, matched: Boolean): Slot =
        of(parameter, type, PythonDocumentationProvider.getTypeName(type, context), matched)

      /** A bare type with no parameter name (an expected position whose parameter is unknown). */
      @JvmStatic
      fun ofType(type: PyType?, context: TypeEvalContext, matched: Boolean): Slot =
        Slot("", type, PythonDocumentationProvider.getTypeName(type, context), matched)

      /** Assembles a parameter slot: `name: typeName` when [typeName] is given (an explicit/declared type), or just
       *  the name when it is null (an unannotated parameter); always carries [type] for the grid to colour + link. */
      private fun of(parameter: PyCallableParameter, type: PyType?, @NlsSafe typeName: String?, matched: Boolean): Slot {
        val prefix = containerPrefix(parameter)
        val name = parameter.name ?: return Slot("", type, typeName.orEmpty(), matched)
        return if (typeName == null) Slot("$prefix$name", type, "", matched)
               else Slot("$prefix$name: ", type, typeName, matched)
      }
    }
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
    // The provided arguments are one PROVIDED (red) row; each candidate signature is an EXPECTED (green) row. So the
    // overload report colours its mismatches exactly like the structural diff — red provided vs green expected, each
    // over a background — because it goes through the SAME shared grid with each row tagged by its side.
    val rows = buildList {
      add(PyTypeDiffGrid.Row(PyPsiBundle.message("INSP.type.checker.argument.types.label"),
                             rowCells(argumentSlots, columnCount), PyTypeDiffGrid.Side.PROVIDED))
      expectedRows.forEachIndexed { i, row ->
        val label = if (i == 0) PyPsiBundle.message("INSP.type.checker.expected.one.of.label") else ""
        add(PyTypeDiffGrid.Row(label, rowCells(row, columnCount), PyTypeDiffGrid.Side.EXPECTED))
      }
    }
    @NlsSafe val headerHtml = header.tooltip.removeSurrounding("<html>", "</html>")
    return PyTypeDiffGrid.tooltip(HtmlChunk.raw(headerHtml), rows)
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
        cells.add(PyTypeDiffGrid.typeValue(slot.pyType, slot.type, mismatch = !slot.matched, suffix = suffix))
      }
    }
    cells.add(PyTypeDiffGrid.delim(")"))
    return cells
  }

  private fun tupleText(slots: List<Slot>): String = slots.joinToString(", ", "(", ")") { it.text }
}
