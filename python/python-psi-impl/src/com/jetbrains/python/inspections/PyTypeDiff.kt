// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections

import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.StackOverflowPreventedException
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.HtmlChunk
import com.jetbrains.python.ProtectionLevel
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.ast.PyAstSingleStarParameter
import com.jetbrains.python.ast.PyAstSlashParameter
import com.jetbrains.python.documentation.PythonDocumentationProvider
import com.jetbrains.python.inspections.PyTypeDiff.columns
import com.jetbrains.python.inspections.PyTypeDiff.diffTooltip
import com.jetbrains.python.inspections.PyTypeDiffGrid.Cell
import com.jetbrains.python.psi.types.PyCallableParameter
import com.jetbrains.python.psi.types.PyCallableParameterImpl
import com.jetbrains.python.psi.types.PyCallableParameterListType
import com.jetbrains.python.psi.types.PyCallableType
import com.jetbrains.python.psi.types.PyClassType
import com.jetbrains.python.psi.types.PyCollectionType
import com.jetbrains.python.psi.types.PyFunctionType
import com.jetbrains.python.psi.types.PyInferredVarianceJudgment
import com.jetbrains.python.psi.types.PyParamSpecType
import com.jetbrains.python.psi.types.PyTupleType
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.PyTypeChecker
import com.jetbrains.python.psi.types.PyTypeParameterType
import com.jetbrains.python.psi.types.PyTypeVarTupleType
import com.jetbrains.python.psi.types.PyTypeVarType
import com.jetbrains.python.psi.types.PyUnionType
import com.jetbrains.python.psi.types.TypeEvalContext

/**
 * Renders a side-by-side structural "type diff" for the editor-hover tooltip of a type mismatch. The two types
 * are laid out as two aligned, code-styled rows (via [PyTypeDiffGrid]) whose matching sub-parts line up vertically
 * and whose incompatible sub-parts are highlighted. The first (top) row is the expected type, the second is the
 * actual (provided) one.
 *
 * The diff recurses into structure that both types share, aligning the corresponding pieces:
 * - **callables** — parameter names and types in separate aligned columns, defaults as `= ...`, `*args`/`/`/`*`
 *   handled (parameters are contravariant; the return type recurses and is covariant);
 * - **tuples** and **generic classes** (`list`/`Sequence`/…) of the same arity — each type argument is aligned
 *   and recursed into, e.g.
 *   ```
 *   tuple[bool, list    [str],    int]
 *   tuple[int,  Sequence[object], str]
 *   ```
 * - anything else is a leaf: the two type names are shown as a single aligned pair.
 *
 * A leaf-vs-leaf mismatch (a plain scalar like `int` vs `str`) has no internal structure to align, so
 * [diffTooltip] returns `null` there and the caller keeps its plain "Expected …, got …" tooltip.
 */
internal object PyTypeDiff {

  /** How deep the structural decomposition may nest before bailing out, to stop recursive types looping forever.
   *  Well beyond any realistically hand-written type, so it never truncates a genuine one. */
  private const val MAX_NESTING_DEPTH = 20

  /** One aligned column: the cell shown on the actual (top) row and the cell shown on the expected (bottom) row. */
  private class Col(val actual: Cell, val expected: Cell)

  /**
   * Builds the diff tooltip for an `expected`/`actual` pair of types with the default headline, or returns
   * `null` when there is no shared structure worth aligning (a plain scalar mismatch, or a bare `...` callable).
   */
  @JvmStatic
  @NlsContexts.Tooltip
  fun diffTooltip(expected: PyType?, actual: PyType?, context: TypeEvalContext): @NlsContexts.Tooltip String? =
    diffTooltip(expected, actual, defaultHeadline(expected, actual), context)

  /**
   * The headline for a plain expected/actual mismatch. When either type is a named callable whose name the
   * aligned rows don't show — a callable `Protocol` (or other class with a `__call__`), or a function — the name
   * is woven into the sentence so the reader knows which type the structural signature belongs to (e.g.
   * "`fn` does not match the expected `Comparator`:"). Otherwise it is the generic provided-vs-expected wording.
   */
  private fun defaultHeadline(expected: PyType?, actual: PyType?): HtmlChunk {
    val expectedName = namedCallableType(expected)
    val actualName = namedCallableType(actual)
    val message = when {
      actualName != null && expectedName != null ->
        PyPsiBundle.message("INSP.type.checker.type.mismatch.header.named.both", actualName, expectedName)
      expectedName != null -> PyPsiBundle.message("INSP.type.checker.type.mismatch.header.named.expected", expectedName)
      actualName != null -> PyPsiBundle.message("INSP.type.checker.type.mismatch.header.named.provided", actualName)
      else -> PyPsiBundle.message("INSP.type.checker.type.mismatch.header")
    }
    // The header templates mark the type names with backticks; render those as `<code>` spans, not literal backticks.
    return HtmlChunk.raw(PyInspectionMessages.codeSpansToHtmlFragment(message))
  }

  /**
   * A user-facing name for a callable type whose structural rendering hides it: a callable `Protocol` (or any
   * other class with a `__call__`), shown by its class name, or a function/lambda, shown by its name. A bare
   * `Callable[...]` has no such name, and a non-callable type's own name (e.g. `list`) already appears in the
   * aligned rows — so both return `null` and the type isn't named in the headline.
   */
  @NlsSafe
  private fun namedCallableType(type: PyType?): String? {
    if (type !is PyCallableType || !type.isCallable) return null
    return when (type) {
      is PyClassType -> type.pyClass.name
      is PyFunctionType -> type.callable.name
      else -> null
    }
  }

  /**
   * Builds the diff tooltip for an `expected`/`actual` pair of types, showing [headline] (an inspection's own
   * message) above the diff. Returns `null` when there is no shared structure worth aligning.
   */
  @JvmStatic
  @NlsContexts.Tooltip
  fun diffTooltip(expected: PyType?, actual: PyType?, headline: HtmlChunk, context: TypeEvalContext): @NlsContexts.Tooltip String? {
    if (!diffTooltipsEnabled()) return null
    val columns = recursionSafe { structuredColumns(expected, actual, context) } ?: return null
    return render(columns, headline)
  }

  /**
   * Builds the diff tooltip comparing two parameter lists only (no return type), for overload-vs-implementation
   * and method-override signature mismatches. The given [headline] is shown above the diff.
   */
  @JvmStatic
  @NlsContexts.Tooltip
  fun paramsDiffTooltip(
    expectedParameters: List<PyCallableParameter>,
    actualParameters: List<PyCallableParameter>,
    headline: HtmlChunk,
    context: TypeEvalContext,
  ): @NlsContexts.Tooltip String {
    if (!diffTooltipsEnabled()) return plainTooltip(headline)
    // `self`/`cls` is the implicit receiver, not part of the overridable signature — its type legitimately differs
    // between base and override (`Self@A` vs `Self@B`), so it must never be shown as a mismatch; drop it from both.
    val columns = recursionSafe { parameterListColumns(dropImplicitSelf(expectedParameters), dropImplicitSelf(actualParameters), context) }
                  ?: return plainTooltip(headline)
    return render(columns, headline)
  }

  /** Whether the structural side-by-side type-diff tooltips are enabled (registry-gated, off by default). When off,
   *  inspections fall back to their plain message tooltip; the Problems-view description is unaffected either way. */
  @JvmStatic
  fun diffTooltipsEnabled(): Boolean = Registry.`is`("python.type.checker.diff.tooltip", false)

  private fun dropImplicitSelf(parameters: List<PyCallableParameter>): List<PyCallableParameter> =
    if (parameters.firstOrNull()?.isSelf == true) parameters.drop(1) else parameters

  /**
   * Building the diff evaluates parameter/return types, which can hit the type system's recursion guard on
   * self-referential built-ins like `object`/`type` (it returns a null type in production, throws in tests). The
   * diff is a best-effort enhancement, so a prevented recursion just drops it — the caller keeps the plain message.
   */
  private inline fun recursionSafe(build: () -> List<Col>?): List<Col>? =
    try {
      build()
    }
    catch (_: StackOverflowPreventedException) {
      null
    }

  /** A minimal tooltip showing just [headline], with no diff grid. */
  @NlsContexts.Tooltip
  private fun plainTooltip(headline: HtmlChunk): @NlsContexts.Tooltip String =
    headline.wrapWith("html").toString()

  @NlsContexts.Tooltip
  private fun render(columns: List<Col>, headline: HtmlChunk): @NlsContexts.Tooltip String {
    // The expected signature is shown on top and the provided (actual) one below, each clearly labeled. Like an
    // editor diff, the incompatible parts of the expected type are highlighted in green and those of the provided
    // value in red.
    val rows = listOf(columns.map { it.expected }, columns.map { it.actual })
    val labels = listOf(PyPsiBundle.message("INSP.type.checker.diff.expected.label"),
                        PyPsiBundle.message("INSP.type.checker.diff.actual.label"))
    val styles = listOf(PyTypeDiffGrid.MismatchStyle.EXPECTED, PyTypeDiffGrid.MismatchStyle.PROVIDED)
    return PyTypeDiffGrid.tooltip(headline, rows, labels, styles)
  }

  // ---- Structural decomposition -------------------------------------------------------------------------------

  /**
   * The variance of a position in the type tree, controlling which direction of assignability must hold there
   * (and therefore which side is the union "source" and how the related-class check is oriented):
   * - **covariant** — the actual value must be assignable to the expected one (return types, tuple elements,
   *   most type arguments);
   * - **contravariant** — the expected value must be accepted by the actual one (callable parameter types);
   * - **invariant** — both must hold (e.g. the element of a `list`, which is invariant). An invariant position
   *   propagates through everything nested in it, so e.g. a `Callable` inside a `list` has invariant parameters.
   */
  private enum class Variance {
    COVARIANT, CONTRAVARIANT, INVARIANT;

    /** The variance of a position nested at [inner] variance inside a position of this variance (the usual
     *  variance "multiplication": invariant is absorbing, otherwise like signs multiply). */
    fun then(inner: Variance): Variance = when {
      this == INVARIANT || inner == INVARIANT -> INVARIANT
      this == inner -> COVARIANT
      else -> CONTRAVARIANT
    }
  }

  /** Whether the [actual]/[expected] pair is incompatible at a position of the given [variance]. */
  private fun typesMismatch(actual: PyType?, expected: PyType?, variance: Variance, context: TypeEvalContext): Boolean =
    when (variance) {
      Variance.COVARIANT -> !PyTypeChecker.match(expected, actual, context)
      Variance.CONTRAVARIANT -> !PyTypeChecker.match(actual, expected, context)
      Variance.INVARIANT -> !PyTypeChecker.match(expected, actual, context) || !PyTypeChecker.match(actual, expected, context)
    }

  /** The declared (or inferred) variance of the [index]th type argument of [generic], mirroring
   *  `PyTypeChecker.findTypeParameterVariance`: tuples are covariant, unknown parameters default to covariant. */
  private fun declaredVariance(generic: PyClassType, index: Int, context: TypeEvalContext): Variance {
    if (generic is PyTupleType) return Variance.COVARIANT
    val definition = PyTypeChecker.findGenericDefinitionType(generic.pyClass, context)
    val typeParameter = definition?.elementTypes?.getOrNull(index) as? PyTypeParameterType ?: return Variance.COVARIANT
    if (typeParameter !is PyTypeVarType) return Variance.COVARIANT
    return when (PyInferredVarianceJudgment.getDeclaredOrInferredVariance(typeParameter, context)) {
      PyTypeParameterType.Variance.CONTRAVARIANT -> Variance.CONTRAVARIANT
      PyTypeParameterType.Variance.INVARIANT -> Variance.INVARIANT
      else -> Variance.COVARIANT
    }
  }

  /** The columns for one aligned pair: shared structure recursed into, or a single leaf pair. */
  private fun columns(expected: PyType?, actual: PyType?, context: TypeEvalContext, variance: Variance = Variance.COVARIANT, depth: Int = 0): List<Col> =
    structuredColumns(expected, actual, context, variance, depth) ?: leafColumns(expected, actual, context, variance)

  /** Like [columns] but returns `null` when neither side decomposes into alignable structure. */
  private fun structuredColumns(expected: PyType?, actual: PyType?, context: TypeEvalContext, variance: Variance = Variance.COVARIANT, depth: Int = 0): List<Col>? {
    // Recursive types (e.g. `class Node(list[Node])`) would otherwise decompose forever — and we descend the
    // structure directly rather than through PyTypeChecker, so there is no shared cycle guard. Past the limit,
    // stop decomposing and let the caller fall back to a flat leaf/cell.
    if (depth > MAX_NESTING_DEPTH) return null
    if (expected is PyCallableType && actual is PyCallableType && expected.isCallable && actual.isCallable) {
      callableColumns(expected, actual, context, variance, depth)?.let { return it }
    }
    tupleColumns(expected, actual, context, variance, depth)?.let { return it }
    genericColumns(expected, actual, context, variance, depth)?.let { return it }
    return null
  }

  private fun leafColumns(expected: PyType?, actual: PyType?, context: TypeEvalContext, variance: Variance = Variance.COVARIANT): List<Col> {
    val actualName = PythonDocumentationProvider.getTypeName(actual, context)
    val expectedName = PythonDocumentationProvider.getTypeName(expected, context)
    val mismatch = typesMismatch(actual, expected, variance, context)
    // The source side is the one whose values must be accepted by the other, so its union members may fail
    // individually: covariant → the actual value must be assignable to the expected type (actual is the source);
    // contravariant → the expected value must be accepted by the actual one (expected is the source). An
    // invariant position needs both directions, so neither side is a single source — the whole cell is shown.
    return when (variance) {
      Variance.COVARIANT -> listOf(Col(
        unionAwareCell(actual, actualName, mismatch, context) { !PyTypeChecker.match(expected, it, context) },
        PyTypeDiffGrid.value(expectedName, mismatch),
      ))
      Variance.CONTRAVARIANT -> listOf(Col(
        PyTypeDiffGrid.value(actualName, mismatch),
        unionAwareCell(expected, expectedName, mismatch, context) { !PyTypeChecker.match(actual, it, context) },
      ))
      Variance.INVARIANT -> listOf(Col(
        PyTypeDiffGrid.value(actualName, mismatch),
        PyTypeDiffGrid.value(expectedName, mismatch),
      ))
    }
  }

  private fun delimColumn(@NlsSafe text: String): Col = Col(PyTypeDiffGrid.delim(text), PyTypeDiffGrid.delim(text))

  /**
   * A value cell for [type] (pre-rendered as [typeName]). When the type is a union and the column is a
   * [mismatch], the members are rendered individually and only those for which [isBadMember] is true are
   * highlighted — so only the part of the union that actually fails stands out. Otherwise the whole value is
   * rendered (highlighted iff [mismatch]).
   */
  private fun unionAwareCell(
    type: PyType?,
    @NlsSafe typeName: String,
    mismatch: Boolean,
    context: TypeEvalContext,
    alignRight: Boolean = false,
    @NlsSafe suffix: String = "",
    isBadMember: (PyType?) -> Boolean,
  ): Cell {
    val members = (type as? PyUnionType)?.members
    if (!mismatch || members == null || members.size < 2) {
      return PyTypeDiffGrid.value(typeName, mismatch, alignRight, suffix)
    }
    val segments = mutableListOf<PyTypeDiffGrid.Segment>()
    members.forEachIndexed { i, member ->
      if (i > 0) segments.add(PyTypeDiffGrid.segmentDelim(" | "))
      segments.add(PyTypeDiffGrid.segment(PythonDocumentationProvider.getTypeName(member, context), isBadMember(member)))
    }
    return PyTypeDiffGrid.segmented(segments, alignRight, suffix)
  }

  private fun tupleColumns(expected: PyType?, actual: PyType?, context: TypeEvalContext, variance: Variance, depth: Int): List<Col>? {
    if (expected !is PyTupleType || actual !is PyTupleType) return null
    if (expected.isHomogeneous || actual.isHomogeneous) return null
    if (expected.elementCount != actual.elementCount || expected.elementCount == 0) return null
    // A tuple is covariant in its elements, so each element keeps the tuple's own variance.
    return bracketed("tuple", "tuple", argumentColumns(expected.elementTypes, actual.elementTypes, context, depth) { variance })
  }

  private fun genericColumns(expected: PyType?, actual: PyType?, context: TypeEvalContext, variance: Variance, depth: Int): List<Col>? {
    // Tuples are a `PyClassType` too, but they're aligned by [tupleColumns]; a tuple reaching here was
    // rejected there (homogeneous, or mismatched arity), so it must stay a flat leaf — not be decomposed as a
    // 1-argument generic (which would drop a homogeneous tuple's `...`).
    if (expected is PyTupleType || actual is PyTupleType) return null
    val expectedClass = expected as? PyClassType ?: return null
    val actualClass = actual as? PyClassType ?: return null
    val expectedArgs = (expectedClass as? PyCollectionType)?.elementTypes.orEmpty()
    val actualArgs = (actualClass as? PyCollectionType)?.elementTypes.orEmpty()
    if (expectedArgs.isEmpty() && actualArgs.isEmpty()) return null
    // Only align type arguments when the two origins are related — the same class, or one a subclass of the other
    // (either direction; the per-argument variance below decides what is actually incompatible). Otherwise they
    // are unrelated and shown as a single red leaf pair.
    if (!actualClass.pyClass.isSubclass(expectedClass.pyClass, context) &&
        !expectedClass.pyClass.isSubclass(actualClass.pyClass, context)) return null
    val expectedName = expectedClass.pyClass.name ?: return null
    val actualName = actualClass.pyClass.name ?: return null
    // The container kinds themselves are incompatible when the actual class isn't a subclass of the expected one
    // (e.g. a `Sequence` assigned where a `list` is expected): then the base names are red too, not just the
    // differing type arguments. The reverse (a `list` where a `Sequence` is expected) is a fine container kind.
    val baseMismatch = !actualClass.pyClass.isSubclass(expectedClass.pyClass, context)
    val definition = PyTypeChecker.findGenericDefinitionType(expectedClass.pyClass, context)
    // A `ParamSpec` generic (`A[**P]`) is parameterized by a parameter list, so its arguments are rendered
    // exactly like a callable's parameters (parens, aligned names, per-type decomposition) rather than as plain
    // type arguments. `P` is invariant in the class, so the parameters are compared invariantly.
    if (definition?.elementTypes?.any { it is PyParamSpecType } == true) {
      val paramColumns = parameterListColumns(paramSpecParameters(expectedArgs), paramSpecParameters(actualArgs), context, Variance.INVARIANT, depth)
      return bracketed(actualName, expectedName, paramColumns, baseMismatch)
    }
    // Same arity: align type arguments one-to-one, each at the container position's variance composed with the
    // parameter's declared variance — so a `list` element (invariant) is invariant, `Sequence` (covariant) keeps
    // it, etc.
    val argColumns =
      if (expectedArgs.size == actualArgs.size)
        argumentColumns(expectedArgs, actualArgs, context, depth) { i -> variance.then(declaredVariance(expectedClass, i, context)) }
      // Differing arity is only alignable for a variadic generic (parameterized by a `TypeVarTuple`): it absorbs
      // the differing middle run; any leftover argument is shown as extra (red).
      else variadicArgColumns(definition, expectedClass, expectedArgs, actualArgs, variance, context, depth) ?: return null
    return bracketed(actualName, expectedName, argColumns, baseMismatch)
  }

  /** The parameters captured by a `ParamSpec` argument: a `[a, b]`-style list contributes its parameters
   *  directly; a bare argument list (`A[int, str]`) is a series of anonymous positional parameters. */
  private fun paramSpecParameters(args: List<PyType?>): List<PyCallableParameter> {
    (args.singleOrNull() as? PyCallableParameterListType)?.let { return it.getParameters() }
    return args.map { PyCallableParameterImpl.nonPsi(it) }
  }

  /** One aligned generic type-argument: present on each side or not (a `TypeVarTuple` may absorb more arguments
   *  on one side than the other, leaving extras with no counterpart). */
  private class ArgSlot(
    val actual: PyType?,
    val expected: PyType?,
    val hasActual: Boolean,
    val hasExpected: Boolean,
    val variance: Variance,
  )

  /**
   * The column(s) for the type arguments of a variadic generic whose two instantiations have different arity.
   * Returns `null` when the generic isn't actually variadic (no `TypeVarTuple` in its [definition]) and so the
   * differing arity can't be aligned. The arguments fixed before/after the `TypeVarTuple` are matched up
   * positionally (from the left and from the right respectively); the `TypeVarTuple` itself absorbs the run in
   * between, aligned from the left, with any surplus argument on either side shown as an extra red cell.
   */
  private fun variadicArgColumns(
    definition: PyCollectionType?,
    generic: PyClassType,
    expectedArgs: List<PyType?>,
    actualArgs: List<PyType?>,
    variance: Variance,
    context: TypeEvalContext,
    depth: Int,
  ): List<Col>? {
    if (definition == null) return null
    // The TypeVarTuple's index is exactly the count of fixed parameters before it.
    val fixedLeft = definition.elementTypes.indexOfFirst { it is PyTypeVarTupleType }
    if (fixedLeft < 0) return null
    val fixedRight = definition.elementTypes.size - fixedLeft - 1
    if (expectedArgs.size < fixedLeft + fixedRight || actualArgs.size < fixedLeft + fixedRight) return null

    val slots = mutableListOf<ArgSlot>()
    for (i in 0 until fixedLeft) {
      slots.add(ArgSlot(actualArgs[i], expectedArgs[i], true, true, variance.then(declaredVariance(generic, i, context))))
    }
    val actualMiddle = actualArgs.subList(fixedLeft, actualArgs.size - fixedRight)
    val expectedMiddle = expectedArgs.subList(fixedLeft, expectedArgs.size - fixedRight)
    // The variadic parameter is invariant, so its absorbed elements are too.
    for (m in 0 until maxOf(actualMiddle.size, expectedMiddle.size)) {
      slots.add(ArgSlot(actualMiddle.getOrNull(m), expectedMiddle.getOrNull(m), m < actualMiddle.size, m < expectedMiddle.size, Variance.INVARIANT))
    }
    for (r in 0 until fixedRight) {
      val definitionIndex = fixedLeft + 1 + r
      slots.add(ArgSlot(actualArgs[actualArgs.size - fixedRight + r], expectedArgs[expectedArgs.size - fixedRight + r], true, true,
                        variance.then(declaredVariance(generic, definitionIndex, context))))
    }

    val columns = mutableListOf<Col>()
    slots.forEachIndexed { index, slot ->
      val elementColumns = renderArgSlot(slot, context, depth + 1)
      columns.addAll(if (index < slots.lastIndex) withTrailingComma(elementColumns) else elementColumns)
    }
    return columns
  }

  private fun renderArgSlot(slot: ArgSlot, context: TypeEvalContext, depth: Int): List<Col> {
    if (slot.hasActual && slot.hasExpected) return columns(slot.expected, slot.actual, context, slot.variance, depth)
    // A surplus argument on one side has no counterpart, so it is the incompatibility: shown red, while the side
    // that lacks it gets an empty mismatch cell the grid paints as a missing-position block across the column.
    val actualCell = if (slot.hasActual) PyTypeDiffGrid.value(PythonDocumentationProvider.getTypeName(slot.actual, context), mismatch = true)
                     else PyTypeDiffGrid.value("", mismatch = true)
    val expectedCell = if (slot.hasExpected) PyTypeDiffGrid.value(PythonDocumentationProvider.getTypeName(slot.expected, context), mismatch = true)
                       else PyTypeDiffGrid.value("", mismatch = true)
    return listOf(Col(actualCell, expectedCell))
  }

  /** Wraps inner type-argument [inner] columns with the base name and brackets: `name[ … ]`. The base name is a
   *  type name, so it gets the normal (value) color — never muted; only the brackets and commas are muted
   *  delimiters. It is highlighted red on both sides when [baseMismatch] — the container kinds themselves are
   *  incompatible (e.g. a `Sequence` where a `list` is expected), not just their type arguments. */
  private fun bracketed(@NlsSafe actualName: String, @NlsSafe expectedName: String, inner: List<Col>, baseMismatch: Boolean = false): List<Col> =
    buildList {
      add(Col(PyTypeDiffGrid.value(actualName, baseMismatch), PyTypeDiffGrid.value(expectedName, baseMismatch)))
      add(delimColumn("["))
      addAll(inner)
      add(delimColumn("]"))
    }

  /** The inner aligned columns (with separating commas) for same-arity type arguments — without the surrounding
   *  base name or brackets. Each argument is recursed into at the variance given by [elementVariance]. */
  private fun argumentColumns(
    expectedArgs: List<PyType?>,
    actualArgs: List<PyType?>,
    context: TypeEvalContext,
    depth: Int,
    elementVariance: (Int) -> Variance,
  ): List<Col> {
    val columns = mutableListOf<Col>()
    expectedArgs.indices.forEach { i ->
      val elementColumns = columns(expectedArgs[i], actualArgs[i], context, elementVariance(i), depth + 1)
      // The comma after an element hugs it (and the padding falls after the comma), so attach it as a suffix
      // of the element's last column rather than as a separate column.
      columns.addAll(if (i < expectedArgs.lastIndex) withTrailingComma(elementColumns) else elementColumns)
    }
    return columns
  }

  private fun withTrailingComma(elementColumns: List<Col>): List<Col> {
    if (elementColumns.isEmpty()) return elementColumns
    val last = elementColumns.last()
    val withComma = Col(PyTypeDiffGrid.withSuffix(last.actual, ", "), PyTypeDiffGrid.withSuffix(last.expected, ", "))
    return elementColumns.dropLast(1) + withComma
  }

  // ---- Callable layout ----------------------------------------------------------------------------------------

  private fun callableColumns(expected: PyCallableType, actual: PyCallableType, context: TypeEvalContext, variance: Variance = Variance.COVARIANT, depth: Int = 0): List<Col>? {
    val expectedParameters = expected.getParameters(context) ?: return null
    val actualParameters = actual.getParameters(context) ?: return null
    // Parameters are contravariant relative to the callable's own position; the return type keeps it.
    val parameterVariance = variance.then(Variance.CONTRAVARIANT)
    val columns = parameterListColumns(expectedParameters, actualParameters, context, parameterVariance, depth).toMutableList()
    columns.add(delimColumn(" -> "))
    columns.addAll(columns(expected.getReturnType(context), actual.getReturnType(context), context, variance, depth + 1))
    return columns
  }

  private fun parameterListColumns(
    expectedParameters: List<PyCallableParameter>,
    actualParameters: List<PyCallableParameter>,
    context: TypeEvalContext,
    variance: Variance = Variance.CONTRAVARIANT,
    depth: Int = 0,
  ): List<Col> {
    val expectedLayout = layout(expectedParameters, context)
    val actualLayout = layout(actualParameters, context)
    // When the expected signature names none of its parameters (e.g. a bare `Callable[[str, int], int]`), the
    // actual parameter names are just noise on the rows that already line up — so they are hidden, except on the
    // parameters that actually differ, where the name still helps the reader spot which one is wrong.
    val hideMatchedNames = expectedLayout.columns.isNotEmpty() && expectedLayout.columns.all { it.rawName == null }

    val columns = mutableListOf(Col(PyTypeDiffGrid.delim("(" + actualLayout.leading), PyTypeDiffGrid.delim("(" + expectedLayout.leading)))
    for (slot in alignParameters(actualLayout.columns, expectedLayout.columns, context, variance)) {
      columns.addAll(renderSlot(slot, hideMatchedNames, context, variance, depth))
    }
    columns.add(delimColumn(")"))
    return columns
  }

  /**
   * One aligned grid slot: the actual-side and expected-side parameter shown on it. Either may be `null` for
   * padding, which happens when a `*args`/`**kwargs` container on one side absorbs several parameters from the
   * other: the container is rendered once and the parameters it absorbs are expanded opposite empty cells.
   *
   * [actualTypeMismatch]/[expectedTypeMismatch] are tracked per side because an absorbing container reds as a
   * whole when it rejects *any* of the parameters it absorbs, while each absorbed parameter reds on its own.
   * [expectedAgainst] is the actual-side type the expected cell is checked against (for per-union-member
   * highlighting) — the absorbing container's element type on the cells it absorbs.
   *
   * [actualMissing]/[expectedMissing] mark a one-to-one slot where that side has no parameter at all while the
   * other does (the provided signature lacks a parameter the expected one mandates, or vice versa). The empty
   * side is then painted as a visible "missing position" background block rather than an invisible empty cell.
   * They are never set on a container's absorbed-padding slots, whose empty side is intentional, not missing.
   */
  private class Slot(
    val actual: ParamColumn?,
    val expected: ParamColumn?,
    val actualTypeMismatch: Boolean,
    val expectedTypeMismatch: Boolean,
    val expectedAgainst: PyType?,
    val actualMissing: Boolean = false,
    val expectedMissing: Boolean = false,
  )

  /**
   * Aligns two parameter lists into [Slot]s, mirroring the spec's callable-compatibility mapping but tolerant of
   * mismatches (it keeps going past them so the whole diff can be shown). A `*args`/`**kwargs` container on one
   * side absorbs the run of plain parameters opposite it — each checked against the container's element type —
   * plus the other side's matching container, if any; the container is shown once and the absorbed parameters
   * are expanded against empty padding cells. Everything else is aligned one-to-one.
   */
  private fun alignParameters(actual: List<ParamColumn>, expected: List<ParamColumn>, context: TypeEvalContext, variance: Variance): List<Slot> {
    val slots = mutableListOf<Slot>()
    var i = 0
    var j = 0
    while (i < actual.size || j < expected.size) {
      val actualParam = actual.getOrNull(i)
      val expectedParam = expected.getOrNull(j)
      when {
        // An actual `*args`/`**kwargs` absorbs the opposite run of plain parameters (each checked against the
        // container's element type) plus the expected container, if present. It is shown on the first slot; the
        // absorbed parameters are expanded against empty actual cells on the following slots.
        actualParam != null && actualParam.container && expectedParam != null && !expectedParam.container -> {
          val absorbed = mutableListOf<ParamColumn>()
          while (j < expected.size && !expected[j].container) absorbed.add(expected[j++])
          if (j < expected.size) absorbed.add(expected[j++])
          val containerReds = absorbed.any { typesMismatch(actualParam.matchType, it.matchType, variance, context) }
          absorbed.forEachIndexed { index, e ->
            val reds = typesMismatch(actualParam.matchType, e.matchType, variance, context)
            slots.add(Slot(if (index == 0) actualParam else null, e, if (index == 0) containerReds else false, reds, actualParam.matchType))
          }
          i++
        }
        // Symmetric: an expected `*args`/`**kwargs` absorbs the opposite run of actual parameters.
        expectedParam != null && expectedParam.container && actualParam != null && !actualParam.container -> {
          val absorbed = mutableListOf<ParamColumn>()
          while (i < actual.size && !actual[i].container) absorbed.add(actual[i++])
          if (i < actual.size) absorbed.add(actual[i++])
          val containerReds = absorbed.any { typesMismatch(it.matchType, expectedParam.matchType, variance, context) }
          absorbed.forEachIndexed { index, a ->
            val reds = typesMismatch(a.matchType, expectedParam.matchType, variance, context)
            slots.add(Slot(a, if (index == 0) expectedParam else null, reds, if (index == 0) containerReds else false, expectedParam.matchType))
          }
          j++
        }
        else -> {
          val mismatch = isTypeMismatch(actualParam, expectedParam, variance, context)
          slots.add(Slot(actualParam, expectedParam, mismatch, mismatch, actualParam?.matchType,
                         actualMissing = actualParam == null, expectedMissing = expectedParam == null))
          if (actualParam != null) i++
          if (expectedParam != null) j++
        }
      }
    }
    return slots
  }

  /** Renders one aligned [slot] as its name, type and default grid columns. */
  private fun renderSlot(slot: Slot, hideMatchedNames: Boolean, context: TypeEvalContext, variance: Variance, depth: Int): List<Col> {
    val actual = slot.actual
    val expected = slot.expected
    // A name is the incompatibility when keyword-callable parameters disagree on it, or when one side can't be
    // passed the way the other requires (a keyword-only vs. positional-only parameter — see [isKindMismatch]).
    val nameMismatch = isNameMismatch(actual, expected) || isKindMismatch(actual, expected)
    val defaultMismatch = isDefaultMismatch(actual, expected)
    val showName = !hideMatchedNames || slot.actualTypeMismatch || slot.expectedTypeMismatch || defaultMismatch || nameMismatch
    // Name part: right-aligned so the type parts line up vertically across both rows. It is highlighted only when
    // the names themselves are the incompatibility (keyword-callable parameters must share a name). An anonymous or
    // positional-only parameter opposite a named one has no name to color, so its empty cell is rendered as a
    // missing-position gap by the grid (see [PyTypeDiffGrid.line]), making the absent mandatory name visible.
    val nameColumn = Col(
      PyTypeDiffGrid.value(nameText(actual, hideMatchedNames, showName), nameMismatch, alignRight = true),
      PyTypeDiffGrid.value(nameText(expected, hideMatchedNames, showName), nameMismatch, alignRight = true),
    )
    val typeColumns = parameterTypeColumns(slot, context, variance, depth)
    // The ` = ...` default is its own cell so it can be highlighted on its own (a parameter that drops a
    // required default is incompatible even when the types match). The comma/separators hug it as a suffix.
    val defaultColumn = Col(
      PyTypeDiffGrid.value(actual?.default.orEmpty(), defaultMismatch, suffix = actual?.suffix.orEmpty()),
      PyTypeDiffGrid.value(expected?.default.orEmpty(), defaultMismatch, suffix = expected?.suffix.orEmpty()),
    )
    val columns = listOf(nameColumn) + typeColumns + defaultColumn
    // An absent parameter on one side is empty in every column; mark those cells as a mismatch so the grid paints
    // each as a missing-position gap across the column the other side occupies.
    return when {
      slot.actualMissing -> columns.map { Col(PyTypeDiffGrid.value("", mismatch = true), it.expected) }
      slot.expectedMissing -> columns.map { Col(it.actual, PyTypeDiffGrid.value("", mismatch = true)) }
      else -> columns
    }
  }

  /**
   * The grid column(s) for a parameter's type at the given [variance] (usually contravariant, but e.g. invariant
   * for a parameter of a `Callable` nested in a `list`). When both sides share structure (a generic or tuple
   * whose element types differ, e.g. a `list` of `int` vs a `list` of `str`) the type is decomposed so only the
   * differing sub-part is highlighted — the shared wrapper stays plain. This applies to a `*args`/`**kwargs`
   * container too: its element type is decomposed against the parameter opposite it. Otherwise it is a single
   * cell: when contravariant the expected type is the source whose union members may individually fail and the
   * actual type is the whole acceptor; otherwise both sides are shown whole.
   */
  private fun parameterTypeColumns(slot: Slot, context: TypeEvalContext, variance: Variance, depth: Int): List<Col> {
    val actual = slot.actual
    val expected = slot.expected
    if (actual != null && expected != null) {
      structuredColumns(expected.matchType, actual.matchType, context, variance, depth + 1)?.let { return it }
    }
    val expectedTypeCell =
      if (expected != null && expected.container) PyTypeDiffGrid.value(expected.type, slot.expectedTypeMismatch)
      else if (variance == Variance.CONTRAVARIANT)
        unionAwareCell(expected?.matchType, expected?.type.orEmpty(), slot.expectedTypeMismatch, context) {
          !PyTypeChecker.match(slot.expectedAgainst, it, context)
        }
      else PyTypeDiffGrid.value(expected?.type.orEmpty(), slot.expectedTypeMismatch)
    return listOf(Col(PyTypeDiffGrid.value(actual?.type.orEmpty(), slot.actualTypeMismatch), expectedTypeCell))
  }

  /**
   * The text for a parameter's name cell. A `*args`/`**kwargs` marker is structural, not just a name, so it is
   * always shown — but when names are hidden its identifier is dropped, leaving just `*`/`**` (with a trailing
   * `: ` when the parameter is typed). Ordinary parameter names are shown only when [showName].
   */
  @NlsSafe
  private fun nameText(column: ParamColumn?, hideMatchedNames: Boolean, showName: Boolean): String {
    if (column == null) return ""
    if (column.container) {
      return if (hideMatchedNames) column.prefix + (if (column.type.isNotEmpty()) ": " else "") else column.name
    }
    return if (showName) column.name else ""
  }

  /** A parameter type is incompatible when it fails the assignability the [variance] requires (usually
   *  contravariant — the actual parameter must accept the expected one). A missing side is always a mismatch. */
  private fun isTypeMismatch(actual: ParamColumn?, expected: ParamColumn?, variance: Variance, context: TypeEvalContext): Boolean {
    if (actual == null && expected == null) return false
    if (actual == null || expected == null) return true
    return typesMismatch(actual.matchType, expected.matchType, variance, context)
  }

  /** The actual must keep any default the expected one has — otherwise it requires an argument the expected
   *  signature treats as optional, which makes it unassignable even when the types match. */
  private fun isDefaultMismatch(actual: ParamColumn?, expected: ParamColumn?): Boolean =
    actual != null && expected != null && expected.hasDefault && !actual.hasDefault

  /** Keyword-callable parameters must share a name. Positional-only (`/`) and anonymous parameters are matched
   *  by position, so a name difference there is not an incompatibility. */
  private fun isNameMismatch(actual: ParamColumn?, expected: ParamColumn?): Boolean {
    if (actual == null || expected == null) return false
    if (actual.positionalOnly || expected.positionalOnly) return false
    val actualName = actual.rawName ?: return false
    val expectedName = expected.rawName ?: return false
    return actualName != expectedName
  }

  /** The actual must accept every calling convention the expected one supports: if the expected parameter can be
   *  passed positionally the actual must accept a positional argument (a keyword-only `*, a` can't), and if the
   *  expected one can be passed by keyword the actual must accept it by name (a positional-only `a, /` can't).
   *  Either way the parameter doesn't match because of how it's named/passed, even when the types agree.
   *  Containers are aligned by absorption, not here. */
  private fun isKindMismatch(actual: ParamColumn?, expected: ParamColumn?): Boolean {
    if (actual == null || expected == null || actual.container || expected.container) return false
    val positionalBridgeFails = expected.acceptsPositional && !actual.acceptsPositional
    val keywordBridgeFails = expected.acceptsKeyword && !actual.acceptsKeyword
    return positionalBridgeFails || keywordBridgeFails
  }

  /** One aligned parameter column: a (right-aligned) name part, a (left-aligned) type part, a `= ...` default
   *  part, and a trailing run of commas and `/`/`*` separators. */
  private class ParamColumn(
    @NlsSafe val name: String,
    @NlsSafe val prefix: String,
    @NlsSafe val type: String,
    @NlsSafe val default: String,
    @NlsSafe val suffix: String,
    val matchType: PyType?,
    val hasDefault: Boolean,
    @NlsSafe val rawName: String?,
    val positionalOnly: Boolean,
    val keywordOnly: Boolean,
    val container: Boolean,
  ) {
    /** A keyword-only parameter (after a bare `*` or `*args`) can't be supplied positionally. */
    val acceptsPositional: Boolean get() = !keywordOnly
    /** A positional-only (`/` or anonymous) parameter has no name a caller can pass it by. */
    val acceptsKeyword: Boolean get() = rawName != null && !positionalOnly
  }

  private class Layout(@NlsSafe val leading: String, val columns: List<ParamColumn>)

  /**
   * Splits a parameter list into aligned columns. `/` and `*` separators do not become columns of their own —
   * they are folded into the trailing [ParamColumn.suffix] of the preceding real parameter (or into
   * [Layout.leading] when no parameter precedes them) so the real parameters keep lining up across both signatures.
   */
  private fun layout(parameters: List<PyCallableParameter>, context: TypeEvalContext): Layout {
    data class RealColumn(
      @NlsSafe val name: String,
      @NlsSafe val prefix: String,
      @NlsSafe val type: String,
      val hasDefault: Boolean,
      val matchType: PyType?,
      @NlsSafe val rawName: String?,
      var positionalOnly: Boolean,
      val keywordOnly: Boolean,
      val container: Boolean,
      val separatorsAfter: MutableList<String>,
    )

    val realColumns = mutableListOf<RealColumn>()
    val leadingSeparators = mutableListOf<String>()
    // Everything after a bare `*` (or after a `*args`) is keyword-only and so can't be supplied positionally.
    var keywordOnly = false

    for (parameter in parameters) {
      val separator = separatorToken(parameter)
      if (separator != null) {
        if (realColumns.isEmpty()) leadingSeparators.add(separator) else realColumns.last().separatorsAfter.add(separator)
        // Every parameter before a `/` is positional-only, so its name doesn't participate in matching.
        if (parameter.isPositionOnlySeparator) realColumns.forEach { it.positionalOnly = true }
        if (parameter.isKeywordOnlySeparator) keywordOnly = true
        continue
      }
      val argumentType = parameter.getArgumentType(context)
      val typeName = if (argumentType == null) "" else PythonDocumentationProvider.getTypeName(argumentType, context)
      val prefix = PyMismatchTooltips.containerPrefix(parameter)
      val name = parameter.name

      val (namePart, typePart) = when {
        name == null -> "" to typeName
        typeName.isEmpty() -> prefix + name to ""
        else -> "$prefix$name: " to typeName
      }
      // A leading-double-underscore name is positional-only by convention, so it doesn't participate in
      // name matching either (mirrors PyCallableParameterMapping's categorization).
      val positionalOnly = parameter.protectionLevel == ProtectionLevel.PRIVATE
      realColumns.add(RealColumn(namePart, prefix, typePart, parameter.hasDefaultValue(), argumentType, name, positionalOnly,
                                 keywordOnly, prefix.isNotEmpty(), mutableListOf()))
      // Parameters following a `*args` positional container are keyword-only too.
      if (parameter.isPositionalContainer) keywordOnly = true
    }

    val columns = realColumns.mapIndexed { i, column ->
      val isLast = i == realColumns.lastIndex
      val suffix = column.separatorsAfter.joinToString("") { ", $it" } + (if (isLast) "" else ", ")
      val default = if (column.hasDefault) " = ..." else ""
      ParamColumn(column.name, column.prefix, column.type, default, suffix, column.matchType, column.hasDefault,
                  column.rawName, column.positionalOnly, column.keywordOnly, column.container)
    }
    val leading = leadingSeparators.joinToString("") { "$it, " }
    return Layout(leading, columns)
  }

  private fun separatorToken(parameter: PyCallableParameter): String? = when {
    parameter.isPositionOnlySeparator -> PyAstSlashParameter.TEXT
    parameter.isKeywordOnlySeparator -> PyAstSingleStarParameter.TEXT
    else -> null
  }
}
