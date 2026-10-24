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
import com.jetbrains.python.psi.types.PyTypeRendererFeature
import com.jetbrains.python.psi.types.TypeEvalContext
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
    /** The type name for the aligned grid tooltip: identical to [type] except that literals are shown bare (`1`, not
     *  `Literal[1]`), which the diff prefers. The plain-text [text]/description keeps the full [type]. */
    @NlsSafe val gridType: String,
    val matched: Boolean,
    /** Whether the parameter NAME is highlighted too — set for a wholly missing (unfilled) parameter, where the
     *  whole parameter is the incompatibility, not just its type (a plain type mismatch highlights only the type). */
    val nameMismatch: Boolean = false,
    /** The argument expression of an argument slot, or the argument mapped to a parameter slot (see [mappedTo]). The
     *  tooltip breaks down the type of a parameter slot against an argument only when they share this argument. */
    val argument: PyExpression? = null,
  ) {
    /** The plain `name + type` text, for the Problems-view description. */
    @get:NlsSafe val text: String get() = name + type

    /** A copy of this parameter slot that records the [argument] mapped to it. */
    fun mappedTo(argument: PyExpression?): Slot = Slot(name, pyType, type, gridType, matched, nameMismatch, argument)

    companion object {
      /** A provided argument rendered as `type` or `keyword=type`. An argument always has an (inferred) type, so it
       *  is always rendered — `getTypeName` shows `Any` when it can't be determined. */
      @JvmStatic
      fun argument(argument: PyExpression, type: PyType?, context: TypeEvalContext, matched: Boolean): Slot {
        val name = (argument as? PyKeywordArgument)?.keyword?.let { "$it=" } ?: ""
        return Slot(name, type, typeName(type, context), bareTypeName(type, context), matched, argument = argument)
      }

      /** An expected parameter using its DECLARED type: an unannotated parameter (a null type) shows just its name,
       *  with no `: type`. [nameMismatch] highlights the name for a wholly missing parameter. */
      @JvmStatic
      @JvmOverloads
      fun parameter(parameter: PyCallableParameter, context: TypeEvalContext, matched: Boolean, nameMismatch: Boolean = false): Slot {
        val type = parameter.getType(context)
        val render = !type.isUnknown
        return of(parameter, type, if (render) typeName(type, context) else null,
                  if (render) bareTypeName(type, context) else null, matched, nameMismatch)
      }

      /** An expected parameter shown with a specific [type] (e.g. one substituted at the call site) rather than its
       *  declared one — always rendered (`getTypeName` shows `Any` when unknown). The [type] is REQUIRED, so its
       *  colour and link are never dropped. */
      @JvmStatic
      @JvmOverloads
      fun parameter(parameter: PyCallableParameter, type: PyType?, context: TypeEvalContext, matched: Boolean, nameMismatch: Boolean = false): Slot =
        of(parameter, type, typeName(type, context), bareTypeName(type, context), matched, nameMismatch)

      /** The gap in a candidate row for an argument that the candidate has no parameter for. The grid paints it as a
       *  missing position, and the description leaves it out. */
      @JvmStatic
      fun noParameter(): Slot = Slot("", null, "", "", matched = false, nameMismatch = true)

      /** A bare type with no parameter name (an expected position whose parameter is unknown). */
      @JvmStatic
      fun ofType(type: PyType?, context: TypeEvalContext, matched: Boolean): Slot =
        Slot("", type, typeName(type, context), bareTypeName(type, context), matched)

      @NlsSafe
      private fun typeName(type: PyType?, context: TypeEvalContext): String =
        PythonDocumentationProvider.getTypeName(type, context)

      /** The grid's type name: literals shown bare (`1`, not `Literal[1]`), and a callback protocol shown as the
       *  signature of its `__call__`. */
      @NlsSafe
      private fun bareTypeName(type: PyType?, context: TypeEvalContext): String =
        PythonDocumentationProvider.getTypeName(type, context, PyTypeRendererFeature.BARE_LITERAL,
                                                PyTypeRendererFeature.CALLBACK_PROTOCOL_AS_CALLABLE)

      /** Assembles a parameter slot: `name: typeName` when [typeName] is given (an explicit/declared type), or just
       *  the name when it is null (an unannotated parameter); always carries [type] for the grid to colour + link. */
      private fun of(parameter: PyCallableParameter, type: PyType?, @NlsSafe typeName: String?, @NlsSafe bareTypeName: String?,
                     matched: Boolean, nameMismatch: Boolean): Slot {
        val prefix = containerPrefix(parameter)
        val name = parameter.name ?: return Slot("", type, typeName.orEmpty(), bareTypeName.orEmpty(), matched, nameMismatch)
        return if (typeName == null) Slot("$prefix$name", type, "", "", matched, nameMismatch)
               else Slot("$prefix$name: ", type, typeName, bareTypeName.orEmpty(), matched, nameMismatch)
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
  @JvmOverloads
  @InspectionMessage
  fun description(
    header: PyInspectionMessages.ProblemMessage,
    argumentSlots: List<Slot>,
    expectedRows: List<List<Slot>>,
    expectedTypeParameters: List<@NlsSafe String> = emptyList(),
    hiddenCandidates: Int = 0,
  ): @InspectionMessage String {
    @NlsSafe val expected = expectedRows.mapIndexed { i, row -> expectedTypeParameters.getOrElse(i) { "" } + tupleText(row) }
      .joinToString(", ")
    val hidden = if (hiddenCandidates > 0) ". " + hiddenCandidatesMessage(hiddenCandidates) else ""
    return header.description + ". " +
           PyPsiBundle.message("INSP.type.checker.argument.types.label") + " " + tupleText(argumentSlots) + ". " +
           PyPsiBundle.message("INSP.type.checker.expected.one.of.label") + " " + expected + hidden
  }

  /** The line that counts the candidates the report leaves out because the overload set is too large. */
  @Nls
  private fun hiddenCandidatesMessage(count: Int): String = PyPsiBundle.message("INSP.type.checker.more.overloads.hidden", count)

  /**
   * Styled HTML tooltip for the editor hover. The provided argument types and each candidate signature are
   * rendered as aligned, code-styled rows (via [PyTypeDiffGrid]) so the arguments line up column-by-column with the
   * parameters; the parts that match are muted and only the offending parts stand out. An argument type is broken
   * down against the parameters it maps to, like an assignment diff (see [typeArea]).
   */
  @JvmStatic
  @JvmOverloads
  @NlsContexts.Tooltip
  fun tooltip(
    header: PyInspectionMessages.ProblemMessage,
    argumentSlots: List<Slot>,
    expectedRows: List<List<Slot>>,
    context: TypeEvalContext,
    expectedTypeParameters: List<@NlsSafe String> = emptyList(),
    hiddenCandidates: Int = 0,
  ): @NlsContexts.Tooltip String {
    // When the structural diff is disabled, fall back to the plain description as the tooltip (no aligned grid).
    if (!PyTypeDiff.diffTooltipsEnabled()) {
      return HtmlChunk.text(description(header, argumentSlots, expectedRows, expectedTypeParameters, hiddenCandidates))
        .wrapWith("html").toString()
    }
    // The provided arguments are one PROVIDED (red) row; each candidate signature is an EXPECTED (green) row. So the
    // overload report colours its mismatches exactly like the structural diff — red provided vs green expected, each
    // over a background — because it goes through the SAME shared grid with each row tagged by its side. A generic
    // candidate carries a leading `[T: bound]` type-parameter column (the argument row and non-generic candidates
    // pad it), so the reader sees the type variables the signature is written in.
    val slotRows = listOf(argumentSlots) + expectedRows
    val columnCount = slotRows.maxOf { it.size }
    val areas = (0 until columnCount).map { i -> typeArea(slotRows.map { it.getOrNull(i) }, slotRows.map { i < it.lastIndex }, context) }
    val cellsByRow = slotRows.mapIndexed { r, slots ->
      val typeParameters = if (r == 0) "" else expectedTypeParameters.getOrElse(r - 1) { "" }
      rowCells(typeParameters, slots, areas.map { it.cellsByRow[r] })
    }
    val rows = buildList {
      add(PyTypeDiffGrid.Row(PyPsiBundle.message("INSP.type.checker.argument.types.label"), cellsByRow[0], PyTypeDiffGrid.Side.PROVIDED))
      expectedRows.indices.forEach { i ->
        val label = if (i == 0) PyPsiBundle.message("INSP.type.checker.expected.one.of.label") else ""
        add(PyTypeDiffGrid.Row(label, cellsByRow[i + 1], PyTypeDiffGrid.Side.EXPECTED))
      }
    }
    // Each argument/parameter position is one elidable slot: its name column and its type columns (see [rowCells]).
    // The slots inside a broken-down type are elidable too.
    val slots = buildList {
      var start = SLOT_START
      for (area in areas) {
        add(start..start + area.width)
        area.slots.forEach { add(start + 1 + it.first..start + 1 + it.last) }
        start += 1 + area.width
      }
    }
    @NlsSafe val headerHtml = header.tooltip.removeSurrounding("<html>", "</html>")
    val footer = if (hiddenCandidates > 0) HtmlChunk.text(hiddenCandidatesMessage(hiddenCandidates)).wrapWith("p") else HtmlChunk.empty()
    return PyTypeDiffGrid.tooltip(HtmlChunk.raw(headerHtml), rows, slots, footer)
  }

  /** The column of the first slot in [rowCells]: after the `[T: bound]` column and the `(`. */
  private const val SLOT_START = 2

  /** The type cells of one argument position on every row, the argument row first, all [width] columns wide. [slots]
   *  are the column ranges of the slots inside a broken-down type, from the first type column. */
  private class TypeArea(val width: Int, val cellsByRow: List<List<PyTypeDiffGrid.Cell>>, val slots: List<IntRange>)

  /**
   * The type cells of one position, given its slot on each row ([slots], the argument row first; `null` where a row has
   * no slot) and whether a comma follows it on each row ([followed]).
   *
   * An unmatched argument is broken down against each candidate parameter that it maps to, like an assignment diff,
   * so only the differing part of the type stands out. The argument row can show only one layout, so the breakdown is
   * used only when the layouts fit into the longest one (see [offsetIn]). A candidate parameter that shares no
   * structure with the argument spans the type columns as one cell. Otherwise every type is one flat cell.
   */
  private fun typeArea(slots: List<Slot?>, followed: List<Boolean>, context: TypeEvalContext): TypeArea {
    val suffixes = followed.map { if (it) ", " else "" }
    val flat = TypeArea(1, slots.mapIndexed { r, slot -> listOf(flatTypeCell(slot, suffixes[r], context)) }, emptyList())
    val argument = slots.first()
    val expression = argument?.argument
    if (argument == null || argument.matched || expression == null) return flat
    val alignments = slots.drop(1).map { slot ->
      if (slot == null || slot.argument !== expression || slot.gridType.isEmpty()) null
      else PyTypeDiff.alignment(slot.pyType, argument.pyType, context)
    }
    val longest = alignments.filterNotNull().maxByOrNull { it.actual.size } ?: return flat
    val width = longest.actual.size
    val offsets = alignments.map { alignment -> alignment?.let { offsetIn(longest, it) ?: return flat } }

    // An argument part stands out when it differs from at least one of the parameters.
    val argumentCells = (0 until width).map { c ->
      val covering = alignments.zip(offsets).mapNotNull { (alignment, offset) ->
        if (alignment == null || offset == null || c - offset !in alignment.actual.indices) null else alignment.actual[c - offset]
      }
      covering.firstOrNull(PyTypeDiffGrid::hasMismatch) ?: longest.actual[c]
    }
    val candidateCells = slots.drop(1).indices.map { i ->
      val slot = slots[i + 1]
      val alignment = alignments[i]
      val offset = offsets[i]
      when {
        alignment != null && offset != null ->
          List(width) { c -> alignment.expected.getOrNull(c - offset) ?: PyTypeDiffGrid.delim("") }
        slot == null -> List(width) { PyTypeDiffGrid.delim("") }
        else -> PyTypeDiffGrid.spanning(flatTypeCell(slot, "", context), width)
      }
    }
    val cellsByRow = (listOf(argumentCells) + candidateCells).mapIndexed { r, cells -> withTrailingSuffix(cells, suffixes[r]) }
    return TypeArea(width, cellsByRow, longest.slots)
  }

  /**
   * The column where the argument layout of [alignment] starts inside the argument layout of [longest], or `null` when
   * it does not fit. It fits when its columns show the same argument text there, and the other columns of [longest]
   * show only separators or gaps on the argument row. So a candidate `Callable[[int], None]` fits into a candidate
   * `Callable[[int], None] | None`: the second one adds only a ` | ` and a gap to the argument row.
   */
  private fun offsetIn(longest: PyTypeDiff.Alignment, alignment: PyTypeDiff.Alignment): Int? {
    val layout = longest.actual.map(PyTypeDiffGrid::visibleText)
    val text = alignment.actual.map(PyTypeDiffGrid::visibleText)
    return (0..layout.size - text.size).firstOrNull { offset ->
      layout.subList(offset, offset + text.size) == text &&
      layout.indices.all { c ->
        c - offset in text.indices || longest.actual[c].kind == PyTypeDiffGrid.Kind.DELIM || layout[c].isBlank()
      }
    }
  }

  /** A slot's whole type as one cell, red or green when the slot is unmatched; an empty cell for a missing slot. A union
   *  shows its members one by one, so each keeps its syntax colour and link. */
  private fun flatTypeCell(slot: Slot?, @NlsSafe suffix: String, context: TypeEvalContext): PyTypeDiffGrid.Cell =
    if (slot == null) PyTypeDiffGrid.delim("")
    else PyTypeDiff.typeCell(slot.pyType, slot.gridType, mismatch = !slot.matched, context, suffix)

  /** Attaches [suffix] to the last cell, so the comma hugs the type. A spanning cell covers the whole area, so it takes
   *  the suffix itself. */
  private fun withTrailingSuffix(cells: List<PyTypeDiffGrid.Cell>, @NlsSafe suffix: String): List<PyTypeDiffGrid.Cell> {
    if (suffix.isEmpty() || cells.isEmpty()) return cells
    val target = if (cells.first().span > 1) 0 else cells.lastIndex
    return cells.mapIndexed { i, cell -> if (i == target) PyTypeDiffGrid.withSuffix(cell, suffix) else cell }
  }

  /**
   * One row as [PyTypeDiffGrid] cells: the `[T: bound]` column, the `(`, then for each position a right-aligned name
   * cell and the position's type cells from [typeCells] (so the types line up), then the `)`. An unmatched slot's type
   * is red, but its name is never highlighted.
   */
  private fun rowCells(@NlsSafe typeParameters: String, slots: List<Slot>, typeCells: List<List<PyTypeDiffGrid.Cell>>): List<PyTypeDiffGrid.Cell> {
    // A leading `[T: bound]` column, present only on generic candidates; the argument row and non-generic candidates
    // pass an empty prefix (padded to this column's width) so every `(` still lines up.
    val cells = mutableListOf(PyTypeDiffGrid.value(typeParameters, mismatch = false), PyTypeDiffGrid.delim("("))
    typeCells.forEachIndexed { i, types ->
      val slot = slots.getOrNull(i)
      cells.add(if (slot == null) PyTypeDiffGrid.delim("") else PyTypeDiffGrid.value(slot.name, mismatch = slot.nameMismatch, alignRight = true))
      cells.addAll(types)
    }
    cells.add(PyTypeDiffGrid.delim(")"))
    return cells
  }

  @NlsSafe
  private fun tupleText(slots: List<Slot>): String = slots.filter { it.text.isNotEmpty() }.joinToString(", ", "(", ")") { it.text }
}
