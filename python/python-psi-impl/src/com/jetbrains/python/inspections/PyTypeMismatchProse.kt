// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections

import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.inspections.PyInspectionMessages.CodifiedParam
import com.jetbrains.python.inspections.PyInspectionMessages.ProblemMessage
import com.jetbrains.python.psi.types.PyMismatchStep
import com.jetbrains.python.psi.types.PyTypeMismatchExplanation
import com.jetbrains.python.psi.types.TypedDictKeyProblem
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting

/**
 * Renders a [PyTypeMismatchExplanation] tree as **prose** rather than the raw one-line-per-node indented list.
 *
 * The tree that [com.jetbrains.python.psi.types.PyTypeChecker.explainMismatch] produces is mostly a *linear
 * chain* of single-child "descent" frames (a protocol check, then the offending attribute, then its type, …)
 * bottoming out in one terminal reason. Showing one indented line per frame forces the reader to walk the whole
 * chain; instead this renderer **folds each such chain into one sentence** whose subject is the type that failed
 * and whose object is an access path (`inner.value`) into it — so arbitrary nesting depth becomes path length,
 * not indentation. Indentation survives only at genuine *branches* (a union tried against each member), where
 * there really are several parallel reasons to keep apart.
 *
 * Two failure kinds also carry an **educational** clause the raw tree can't: an invariant type parameter
 * rejecting a subtype (why a `list` of `bool` isn't a `list` of `int` *even though* `bool` is a subtype of
 * `int`), and a contravariant callable parameter that is too narrow. Those come from the node's [PyMismatchStep].
 *
 * When the mismatch is a **call argument** ([CallSite] supplied), the breakdown is reframed as a two-line
 * requirement/actual sentence that names the callee, the parameter and the argument expression, e.g.
 * ```
 * `f()` needs parameter `items` of type `Iterable[str]` to have attribute `__iter__`
 * `xs()` is `Page`, which has no `__iter__`
 * ```
 * This replaces the "Expected type …, got …" headline for the recognized shapes; anything else falls back to
 * that headline plus the plain folded breakdown.
 *
 * Every node keeps its localized [PyTypeMismatchExplanation.message] as the source of truth: a node whose
 * [PyMismatchStep] is missing or whose shape this renderer doesn't recognize falls back to that message
 * verbatim (and its children are recursed), so the prose is never *worse* than the plain tree, only better
 * where structure is known.
 */
@ApiStatus.Internal
object PyTypeMismatchProse {

  /** One rendered prose line: its nesting [depth] (0 at the top; only branches go deeper) and its [message]. */
  class ProseLine(val depth: Int, val message: ProblemMessage)

  /**
   * The call-site context for an argument mismatch, used to reframe the breakdown as a single requirement/actual
   * sentence. [callee] is the called function rendered as a `name()` span, clickable when it is a named
   * function/method (`null` for an anonymous callee, which disables the reframing); [argument] is the (shortened)
   * argument expression text; [parameterName] is the target parameter's name (`null` for a positional-only slot);
   * [parameterType] and [actualType] are the expected and actual argument types, pre-rendered as clickable spans.
   * [actualIsLiteral] is set when the argument is a literal (e.g. `"a"`, `42`), whose value the argument expression
   * already shows — so the "…, but `arg` is `Literal[…]`" clause would only restate it and is dropped.
   */
  class CallSite(
    val callee: CodifiedParam?,
    @NlsSafe val argument: String,
    @NlsSafe val parameterName: String?,
    val parameterType: CodifiedParam,
    val actualType: CodifiedParam,
    val actualIsLiteral: Boolean = false,
  )

  /** Flattens [roots] into prose lines. Exposed for tests, which assert on the plain [ProblemMessage.description]. */
  @VisibleForTesting
  fun proseLines(roots: List<PyTypeMismatchExplanation>): List<ProseLine> {
    val out = mutableListOf<ProseLine>()
    for (root in roots) emit(root, 0, out)
    return out
  }

  /**
   * The call-site framing for [root], or `null` only when [callSite] lacks a callee. A few shapes get a tailored
   * two-line requirement/actual sentence (a missing attribute, a wrong attribute type, a plain nominal, a union);
   * every other shape (a variance failure, a callable wrong in a parameter or return, a TypedDict key, …) gets a
   * generic "`f()` needs parameter `a` of type `T`" / "`x` is `Y`" headline followed by the folded breakdown
   * beneath it — so the reader always sees the call-site framing, and never the bare "Expected type …" headline.
   * Exposed for tests.
   */
  @VisibleForTesting
  fun argumentProseLines(callSite: CallSite, root: PyTypeMismatchExplanation): List<ProseLine>? {
    if (callSite.callee == null) return null

    // A union. Two directions, told apart by [NoUnionMember.actualIsUnion]:
    //  - REQUIRED side is the union (a single argument against a union parameter): "f() needs `x` to match `A | B`,
    //    but `arg` is `C`, which matches no member", then one line per member that failed for a *structural* reason.
    //    A member the argument is trivially not (a bare "None is not assignable to int") adds nothing, so it's dropped.
    //  - PROVIDED value is the union (a union argument against a single parameter): the required type is NOT a union,
    //    so "which matches no member" would be nonsense; name the required type plainly ("f() needs `x` of type `T`,
    //    but `arg` is `A | B`") and list the failing members as arms.
    val step = root.step
    if (step is PyMismatchStep.NoUnionMember) {
      val requirement = if (step.actualIsUnion) requirementNominal(callSite) else unionRequirement(callSite, step.union)
      val actual = if (step.actualIsUnion) actualNominal(callSite) else actualUnion(callSite)
      val out = mutableListOf(ProseLine(0, oneLine(callSite, requirement, actual)))
      emitUnionArms(root, 1, out)
      return out
    }

    val chain = foldChain(root) as? Fold.Terminal
    if (chain != null) {
      when (val terminal = chain.step) {
        is PyMismatchStep.Missing ->
          if (chain.subject != null)
            return listOf(ProseLine(0, oneLine(callSite, requirementMissing(callSite, terminal.name), actualMissing(callSite, terminal.name))))
        is PyMismatchStep.Nominal -> when {
          chain.subject != null && !chain.path.isEmpty ->
            return listOf(ProseLine(0, oneLine(callSite, requirementAttribute(callSite, chain.path.render(), terminal.expected),
                                               actualAttribute(callSite, chain.path.render(), terminal.actual))))
          chain.subject == null && chain.path.isEmpty ->
            return listOf(ProseLine(0, oneLine(callSite, requirementNominal(callSite), actualNominal(callSite))))
          else -> {} // a type-argument position on a bare argument: fall through to the generic framing
        }
        else -> {} // a variance / TypedDict / other terminal: fall through to the generic framing
      }
    }

    // Generic framing: the call-site requirement/actual sentence, then the folded breakdown one level deeper. This is
    // where the "prepend the call-site line" for contravariance, invariance and TypedDicts happens: the educational
    // or structural reason keeps its own line(s), but now under a headline that names the callee and argument.
    val out = mutableListOf(ProseLine(0, oneLine(callSite, requirementNominal(callSite), actualNominal(callSite))))
    for (line in proseLines(listOf(root))) out.add(ProseLine(line.depth + 1, line.message))
    return out
  }

  /**
   * Appends [lines] to [builder] as `<br>`-separated tooltip fragments, prefixed with directory-tree guide lines so
   * a dedent is easy to trace back to the parent it lines up under. A line at a genuine *branch* (a parent with two
   * or more children — a callable wrong in both a parameter and its return, a union with several structural members)
   * gets a `├─`/`└─` connector, with a `│` continuation drawn down through every ancestor that still has a sibling
   * below; a single-child "because" descent stays plain indentation, so the common linear chain reads as prose.
   * [leadingBreak] adds a break before the first line so it sits below a preceding headline.
   *
   * The guide column is rendered a little larger than the text so the box-drawing glyphs fill the line height and the
   * `│` segments meet across rows (at the text size they leave a vertical gap in the Swing tooltip).
   */
  fun appendLines(builder: HtmlBuilder, lines: List<ProseLine>, leadingBreak: Boolean) {
    lines.forEachIndexed { index, line ->
      if (leadingBreak || index > 0) builder.br()
      val prefix = treePrefix(lines, index)
      if (prefix.isNotEmpty()) builder.append(HtmlChunk.span().style(GUIDE_STYLE).addRaw(prefix))
      builder.appendRaw(PyInspectionMessages.tooltipFragment(line.message))
    }
  }

  // Monospace so the columns align, and enlarged so the box glyphs' full-height verticals meet between rows.
  private const val GUIDE_STYLE = "font-family:monospace;font-size:130%"

  // Directory-tree guide columns, each three cells wide so connectors, continuations and blanks align.
  @NlsSafe private const val CONNECTOR_MID = "├─&nbsp;"        // a child that has a sibling below it
  @NlsSafe private const val CONNECTOR_END = "└─&nbsp;"        // the last child in its group
  @NlsSafe private const val CONTINUATION = "│&nbsp;&nbsp;"    // an ancestor whose subtree is still open
  @NlsSafe private const val BLANK = "&nbsp;&nbsp;&nbsp;"      // a closed ancestor, or a single-child (prose) indent

  /**
   * The guide-line prefix for the line at [index]: one column per depth level, drawn only where there is a real
   * branch. Depth 0 (a root / the requirement-actual headline pair) gets no prefix; a level whose node is an only
   * child gets a blank column (plain indentation), so a purely linear chain renders with no connectors.
   */
  @NlsSafe
  private fun treePrefix(lines: List<ProseLine>, index: Int): String {
    val depth = lines[index].depth
    if (depth == 0) return ""
    val sb = StringBuilder()
    for (level in 1 until depth) {
      val ancestor = ancestorAtDepth(lines, index, level)
      sb.append(if (ancestor >= 0 && hasFollowingSibling(lines, ancestor)) CONTINUATION else BLANK)
    }
    sb.append(when {
      hasFollowingSibling(lines, index) -> CONNECTOR_MID
      hasPrecedingSibling(lines, index) -> CONNECTOR_END
      else -> BLANK
    })
    return sb.toString()
  }

  /** The nearest earlier line at exactly [level] on the path to [index] (its ancestor), or -1 if there is none. */
  private fun ancestorAtDepth(lines: List<ProseLine>, index: Int, level: Int): Int {
    for (j in index - 1 downTo 0) {
      val d = lines[j].depth
      if (d == level) return j
      if (d < level) return -1
    }
    return -1
  }

  /** Whether the line at [index] has a later sibling (a line at the same depth before the group closes). */
  private fun hasFollowingSibling(lines: List<ProseLine>, index: Int): Boolean {
    val depth = lines[index].depth
    for (j in index + 1 until lines.size) {
      val d = lines[j].depth
      if (d < depth) return false
      if (d == depth) return true
    }
    return false
  }

  /** Whether the line at [index] has an earlier sibling (a line at the same depth within the same group). */
  private fun hasPrecedingSibling(lines: List<ProseLine>, index: Int): Boolean {
    val depth = lines[index].depth
    for (j in index - 1 downTo 0) {
      val d = lines[j].depth
      if (d < depth) return false
      if (d == depth) return true
    }
    return false
  }

  private fun emit(node: PyTypeMismatchExplanation, depth: Int, out: MutableList<ProseLine>) {
    when (val step = node.step) {
      // A transparent grouping of several independent top-level reasons: emit the children at the same depth, so
      // e.g. a callable wrong in both a parameter and its return type shows both lines with no wrapper above them.
      is PyMismatchStep.Combined -> for (child in node.children) emit(child, depth, out)
      // A contravariant callable parameter is a *frame*, not a terminal: state "is called with X, but only accepts Y",
      // then recurse the nested reason X and Y are incompatible (why `A` isn't a `B`). That nested reason is the
      // whole point — dropping it (as a terminal would) leaves the reader with no explanation.
      is PyMismatchStep.ContravariantParameter -> {
        out.add(ProseLine(depth, contravariant(step)))
        for (child in nonTrivialReasons(node)) emit(child, depth + 1, out)
      }
      // A union: the "not assignable to any member" header, then one member-named line per structural failure.
      is PyMismatchStep.NoUnionMember -> {
        out.add(ProseLine(depth, node.message))
        emitUnionArms(node, depth + 1, out)
      }
      // A protocol checked against several members: when MORE THAN ONE fails, describe every failure as an arm that
      // still carries the subject (`Attribute 'x' of 'A' …` / `'A' has no 'y'`) — not just the first. A single
      // failure keeps the folded one-liner (`Attribute 'x' of 'A' is 'str', but 'int' was expected`).
      is PyMismatchStep.Protocol -> {
        if (node.children.size >= 2) {
          out.add(ProseLine(depth, node.message))
          for (child in node.children) emitFolded(child, depth + 1, out, seedSubject = step.subject)
        }
        else emitFolded(node, depth, out)
      }
      else -> emitFolded(node, depth, out)
    }
  }

  /**
   * Emits [node] by folding its single-child descent chain into one line (a terminal reason, drilling into any
   * nested type arguments; or a self-deciding frame), or — when it is a branch/unrecognized shape — its own line
   * followed by its recursed children. [seedSubject] pre-seeds the fold's subject, so a member arm emitted under a
   * protocol frame still reads "Attribute 'x' of 'A' …" rather than losing the owner.
   */
  private fun emitFolded(node: PyTypeMismatchExplanation, depth: Int, out: MutableList<ProseLine>, seedSubject: CodifiedParam? = null) {
    when (val folded = foldChain(node, seedSubject)) {
      is Fold.Terminal -> {
        out.add(ProseLine(depth, composeTerminal(folded)))
        if (folded.typeArgs != null) emitTypeArgs(folded.typeArgs, depth + 1, out)
      }
      is Fold.Decided -> out.add(ProseLine(depth, folded.message))
      null -> {
        // An unrecognized node: keep its own line, then recurse its reasons one level deeper.
        out.add(ProseLine(depth, node.message))
        for (child in node.children) emit(child, depth + 1, out)
      }
    }
  }

  /**
   * The child reasons worth expanding under a frame that already stated the top-level mismatch (a union header, a
   * contravariant parameter): those with structure beyond "the value simply isn't that type". A bare nominal leaf
   * (`None is not assignable to int`) only restates the frame line, so it is dropped; a member that failed on a
   * protocol attribute, an element type, etc. is kept.
   */
  private fun nonTrivialReasons(node: PyTypeMismatchExplanation): List<PyTypeMismatchExplanation> =
    node.children.filterNot { isTrivialReason(it) }

  /**
   * Whether [node] only restates its parent union header: a bare nominal leaf (`None is not assignable to int`), or
   * a per-member [PyMismatchStep.UnionMember] wrapper whose lone reason is itself such a leaf (`str is not
   * assignable to int` over the same). Either adds nothing over the header, so the union arm is dropped.
   */
  private fun isTrivialReason(node: PyTypeMismatchExplanation): Boolean =
    (node.step is PyMismatchStep.Nominal && node.children.isEmpty()) ||
    (node.step is PyMismatchStep.UnionMember && node.children.size == 1 && isTrivialReason(node.children.single()))

  /**
   * Emits the structural arms of a union under its header, each named by the member it failed against, so the
   * reader can tell the lines apart (`'A' needs 'a' to be 'int', but 'C' has 'None'` vs the `'B'`/`'str'` arm) —
   * otherwise two "`Attribute a` of `C`" lines that differ only in the expected type are indistinguishable. An arm
   * whose failing member isn't a protocol (no member name to show) folds normally.
   */
  private fun emitUnionArms(node: PyTypeMismatchExplanation, depth: Int, out: MutableList<ProseLine>) {
    for (child in nonTrivialReasons(node)) {
      // A per-member wrapper (an actual-side union names each failing member) folds through to its reason; an
      // expected-side arm is the reason directly. Either way [foldChain] lands on the member's terminal reason.
      val reason = if (child.step is PyMismatchStep.UnionMember) child.children.singleOrNull() ?: child else child
      val fold = foldChain(reason)
      val arm = if (fold is Fold.Terminal && fold.member != null) composeUnionArm(fold) else null
      when {
        // The reason self-names the member (a missing / wrong-typed attribute): keep one flat arm, drop the wrapper.
        arm != null -> {
          out.add(ProseLine(depth, arm))
          if (fold is Fold.Terminal && fold.typeArgs != null) emitTypeArgs(fold.typeArgs, depth + 1, out)
        }
        // It doesn't fold to a one-line arm. If the reason emits its OWN member-naming header (a protocol frame
        // "`B` is incompatible with protocol `P`"), the "`B` is not assignable to `P`" wrapper only restates it, so
        // drop the wrapper and emit the reason directly. Otherwise (e.g. a callable parameter mismatch that names
        // no member) keep the wrapper line to identify the member, with the reason beneath it.
        child.step is PyMismatchStep.UnionMember ->
          if (reason.step is PyMismatchStep.Protocol) emit(reason, depth, out)
          else {
            out.add(ProseLine(depth, child.message))
            for (grandchild in child.children) emit(grandchild, depth + 1, out)
          }
        else -> emit(child, depth, out)
      }
    }
  }

  /**
   * Drills into a nested type-argument mismatch, one "expected …, got …" line per level (the element of a `list`,
   * then the element of its `set`, …), so the reader sees exactly where deep inside a parameterized type the types
   * diverge rather than a `[1][1]` position path. Stops at the deepest recorded type-argument level.
   */
  private fun emitTypeArgs(node: PyTypeMismatchExplanation, depth: Int, out: MutableList<ProseLine>) {
    val step = node.step
    if (step !is PyMismatchStep.TypeArgument || step.expected == null || step.actual == null) return
    out.add(ProseLine(depth, PyPsiBundle.problemMessage("INSP.type.checker.prose.type.arg.diff", step.expected, step.actual)))
    val deeper = node.children.firstOrNull { it.step is PyMismatchStep.TypeArgument }
    if (deeper != null) emitTypeArgs(deeper, depth + 1, out)
  }

  /** A member-named union arm line, phrased from the member's requirement so the member is the subject. */
  private fun composeUnionArm(chain: Fold.Terminal): ProblemMessage? {
    val member = chain.member ?: return null
    return when (val step = chain.step) {
      is PyMismatchStep.Missing ->
        PyPsiBundle.problemMessage(
          if (step.isMethod) "INSP.type.checker.prose.union.method.missing" else "INSP.type.checker.prose.union.member.missing",
          chain.subject, step.name, member)
      is PyMismatchStep.Nominal ->
        if (chain.path.isEmpty) null // no attribute path to attribute the failure to: fall back to the plain fold
        else PyPsiBundle.problemMessage("INSP.type.checker.prose.union.member.attribute",
                                        member, chain.path.render(), step.expected, chain.subject, step.actual)
      else -> null // a variance / typed-dict arm: fall back to the plain fold
    }
  }

  /** The outcome of walking a single-child descent chain (see [foldChain]). */
  private sealed interface Fold {
    /**
     * The chain reached a terminal [step] (nominal, missing, variance) under [subject] at access [path]. [member]
     * is the protocol the chain was tried against, set only when the chain is a union arm (used to name it).
     * [typeArgs], when set, is the type-argument frame nested inside a parameterized attribute — the renderer
     * shows the attribute's types on the terminal line and then drills into [typeArgs] with a line per level.
     */
    class Terminal(
      val subject: CodifiedParam?,
      val member: CodifiedParam?,
      val path: PathBuilder,
      val step: PyMismatchStep,
      val typeArgs: PyTypeMismatchExplanation? = null,
    ) : Fold

    /** A descent frame decided the failure on its own (no nested reason), e.g. an attribute that isn't writable. */
    class Decided(val message: ProblemMessage) : Fold
  }

  /**
   * Walks the single-child descent chain from [node], accumulating the subject type and an access path, and
   * returns how it bottomed out — [Fold.Terminal] at a terminal reason, [Fold.Decided] when a descent frame
   * decided on its own, or `null` when [node] is a branch (a union, a fan-out) or has an unrecognized shape.
   */
  private fun foldChain(node: PyTypeMismatchExplanation, initialSubject: CodifiedParam? = null): Fold? {
    var subject: CodifiedParam? = initialSubject
    var member: CodifiedParam? = null
    var attributeActual: CodifiedParam? = null
    var attributeExpected: CodifiedParam? = null
    val path = PathBuilder()
    var cur = node
    while (true) {
      when (val step = cur.step ?: return null) {
        is PyMismatchStep.Protocol -> {
          if (subject == null) subject = step.subject
          if (member == null) member = step.expected
        }
        is PyMismatchStep.Attribute -> {
          path.attribute(step.name)
          attributeActual = step.actual
          attributeExpected = step.expected
        }
        // A mismatch inside a parameterized attribute type: don't descend into meaningless `[1][1]` type-argument
        // positions — report the attribute's own types on the terminal line, then hand the type-argument subtree to
        // the renderer to drill into with a readable "expected …, got …" line per level. (A bare generic with no
        // enclosing attribute keeps the positional path.)
        is PyMismatchStep.TypeArgument ->
          if (attributeActual != null && attributeExpected != null)
            return Fold.Terminal(subject, member, path, PyMismatchStep.Nominal(attributeActual, attributeExpected), typeArgs = cur)
          else path.typeArgument(step.oneBasedIndex)
        PyMismatchStep.Return -> path.returnType()

        // Prefer the enclosing attribute's own types over a nested leaf: for `a: list[set[int]]` vs `list[set[None]]`
        // the match records the innermost `None`/`int` directly under the attribute (no type-argument frames), so
        // reporting that leaf reads "a to be int" (wrong — a is a list) and, under an invariant leg, backwards. The
        // attribute's types are the meaningful, correctly-oriented pair.
        is PyMismatchStep.Nominal ->
          return Fold.Terminal(subject, member, path,
                               if (attributeActual != null && attributeExpected != null)
                                 PyMismatchStep.Nominal(attributeActual, attributeExpected)
                               else step)

        is PyMismatchStep.Missing, is PyMismatchStep.Invariant, is PyMismatchStep.TypedDictKey ->
          return Fold.Terminal(subject, member, path, step)

        // Frames that carry their own line AND a nested reason (or are transparent groupings): not silently
        // foldable — [emit]/[emitUnionArms] handle them.
        is PyMismatchStep.ContravariantParameter, is PyMismatchStep.NoUnionMember,
        is PyMismatchStep.UnionMember, PyMismatchStep.Combined ->
          return null
      }
      // Only descent steps reach here; continue folding iff there is exactly one child to descend into.
      when (cur.children.size) {
        1 -> cur = cur.children.single()
        0 -> return Fold.Decided(cur.message) // a descent that decided on its own (e.g. an attribute not writable)
        else -> return null                   // a descent that fans out: fall back to the header + recursed children
      }
    }
  }

  private fun composeTerminal(chain: Fold.Terminal): ProblemMessage = when (val step = chain.step) {
    is PyMismatchStep.Nominal -> nominal(chain.subject, chain.path, step.actual, step.expected)
    is PyMismatchStep.Missing -> missing(chain.subject, step.name, step.isMethod)
    is PyMismatchStep.Invariant -> invariant(step)
    is PyMismatchStep.TypedDictKey -> typedDictKey(step)
    else -> error("non-terminal step ${chain.step} reached composeTerminal")
  }

  /** The invariance line, with an optional "use `Sequence` if you only read from it" hint for list/set/dict. */
  private fun invariant(step: PyMismatchStep.Invariant): ProblemMessage =
    if (step.readOnlyAlternative != null)
      PyPsiBundle.problemMessage("INSP.type.checker.prose.invariant.suggest",
                                 step.owner, step.typeVar, step.actual, step.expected, step.readOnlyAlternative)
    else
      PyPsiBundle.problemMessage("INSP.type.checker.prose.invariant", step.owner, step.typeVar, step.actual, step.expected)

  /** The TypedDict key line: a missing key, a wrong value type, a read-only source key, or a required mismatch. */
  private fun typedDictKey(step: PyMismatchStep.TypedDictKey): ProblemMessage = when (step.problem) {
    TypedDictKeyProblem.MISSING ->
      PyPsiBundle.problemMessage("INSP.type.checker.prose.typed.dict.missing", step.owner, step.key)
    TypedDictKeyProblem.VALUE_TYPE ->
      if (step.actual != null && step.expected != null)
        PyPsiBundle.problemMessage("INSP.type.checker.prose.typed.dict.type", step.owner, step.key, step.actual, step.expected)
      else
        PyPsiBundle.problemMessage("INSP.type.checker.prose.typed.dict.type.plain", step.owner, step.key)
    TypedDictKeyProblem.READONLY ->
      PyPsiBundle.problemMessage("INSP.type.checker.prose.typed.dict.readonly", step.owner, step.key)
    TypedDictKeyProblem.REQUIRED ->
      PyPsiBundle.problemMessage("INSP.type.checker.prose.typed.dict.required", step.owner, step.key)
  }

  /** The "Parameter … is called with X, but only accepts Y" line; identified by name, else 1-based position. */
  private fun contravariant(step: PyMismatchStep.ContravariantParameter): ProblemMessage = when {
    step.name != null ->
      PyPsiBundle.problemMessage("INSP.type.checker.prose.contravariant", step.name, step.required, step.provided)
    step.oneBasedIndex != null ->
      PyPsiBundle.problemMessage("INSP.type.checker.prose.contravariant.indexed", step.oneBasedIndex, step.required, step.provided)
    else ->
      PyPsiBundle.problemMessage("INSP.type.checker.prose.contravariant.unnamed", step.required, step.provided)
  }

  private fun nominal(
    subject: CodifiedParam?,
    path: PathBuilder,
    actual: CodifiedParam,
    expected: CodifiedParam,
  ): ProblemMessage = when {
    path.isReturnOnly ->
      PyPsiBundle.problemMessage("INSP.type.checker.prose.return.nominal", actual, expected)
    subject != null && !path.isEmpty ->
      PyPsiBundle.problemMessage("INSP.type.checker.prose.member.nominal", subject, path.render(), actual, expected)
    !path.isEmpty ->
      PyPsiBundle.problemMessage("INSP.type.checker.prose.path.nominal", path.render(), actual, expected)
    else ->
      PyPsiBundle.problemMessage("INSP.type.checker.type.not.assignable", actual, expected)
  }

  private fun missing(subject: CodifiedParam?, name: String, isMethod: Boolean): ProblemMessage =
    if (subject != null)
      PyPsiBundle.problemMessage(if (isMethod) "INSP.type.checker.prose.method.missing" else "INSP.type.checker.prose.member.missing", subject, name)
    else
      PyPsiBundle.problemMessage(if (isMethod) "INSP.type.checker.breakdown.method.missing" else "INSP.type.checker.breakdown.member.missing", name)

  // ---- call-site requirement/actual lines -------------------------------------------------------------------

  /**
   * Joins a [requirement] with its [actual]-value clause into one sentence ("…, but `arg` is `T`"), or keeps only the
   * requirement when the argument is a literal ([CallSite.actualIsLiteral]) — whose value the argument expression
   * already shows, so "`"a"` is `Literal["a"]`" would only restate it.
   */
  private fun oneLine(callSite: CallSite, requirement: ProblemMessage, actual: ProblemMessage): ProblemMessage =
    if (callSite.actualIsLiteral) requirement
    else PyPsiBundle.problemMessage("INSP.type.checker.prose.arg.line.join", asParam(requirement), asParam(actual))

  /** Re-wraps an already-rendered [ProblemMessage] as a [CodifiedParam] (plain description + rich tooltip), so it can
   *  be embedded verbatim — with its own `<code>` spans intact — inside another template. */
  private fun asParam(message: ProblemMessage): CodifiedParam =
    CodifiedParam(message.description, PyInspectionMessages.tooltipFragment(message))

  /** The "parameter `name` of type `T`" (or "an argument of type `T`") phrase, as one param that already carries
   *  its own `<code>` spans, so the requirement templates embed it without adding another. */
  private fun parameterPhrase(callSite: CallSite): CodifiedParam =
    asParam(
      if (callSite.parameterName != null)
        PyPsiBundle.problemMessage("INSP.type.checker.prose.arg.param.named", callSite.parameterName, callSite.parameterType)
      else
        PyPsiBundle.problemMessage("INSP.type.checker.prose.arg.param.unnamed", callSite.parameterType))

  private fun requirementMissing(callSite: CallSite, name: String): ProblemMessage =
    PyPsiBundle.problemMessage("INSP.type.checker.prose.arg.req.missing", callSite.callee, parameterPhrase(callSite), name)

  private fun actualMissing(callSite: CallSite, name: String): ProblemMessage =
    PyPsiBundle.problemMessage("INSP.type.checker.prose.arg.act.missing", callSite.argument, callSite.actualType, name)

  private fun requirementAttribute(callSite: CallSite, path: String, expected: CodifiedParam): ProblemMessage =
    PyPsiBundle.problemMessage("INSP.type.checker.prose.arg.req.attribute", callSite.callee, parameterPhrase(callSite), path, expected)

  private fun actualAttribute(callSite: CallSite, path: String, actual: CodifiedParam): ProblemMessage =
    PyPsiBundle.problemMessage("INSP.type.checker.prose.arg.act.attribute", callSite.argument, callSite.actualType, path, actual)

  private fun requirementNominal(callSite: CallSite): ProblemMessage =
    PyPsiBundle.problemMessage("INSP.type.checker.prose.arg.req.nominal", callSite.callee, parameterPhrase(callSite))

  private fun actualNominal(callSite: CallSite): ProblemMessage =
    PyPsiBundle.problemMessage("INSP.type.checker.prose.arg.act.nominal", callSite.argument, callSite.actualType)

  private fun unionRequirement(callSite: CallSite, union: CodifiedParam): ProblemMessage =
    if (callSite.parameterName != null)
      PyPsiBundle.problemMessage("INSP.type.checker.prose.arg.req.union.named", callSite.callee, callSite.parameterName, union)
    else
      PyPsiBundle.problemMessage("INSP.type.checker.prose.arg.req.union.unnamed", callSite.callee, union)

  private fun actualUnion(callSite: CallSite): ProblemMessage =
    PyPsiBundle.problemMessage("INSP.type.checker.prose.arg.act.union", callSite.argument, callSite.actualType)

  /**
   * Accumulates an access path from descent steps: attributes join with dots (`inner.value`), type arguments
   * append a bracketed position (`items[1]`). A lone return-type descent is tracked separately so it can read
   * "The return type is …" instead of being forced into the path grammar.
   */
  private class PathBuilder {
    private val sb = StringBuilder()
    private var segments = 0
    var isReturnOnly = false
      private set

    val isEmpty: Boolean get() = sb.isEmpty()

    fun attribute(@NlsSafe name: String) {
      if (sb.isNotEmpty()) sb.append('.')
      sb.append(name)
      segments++
      isReturnOnly = false
    }

    fun typeArgument(oneBasedIndex: Int) {
      sb.append('[').append(oneBasedIndex).append(']')
      segments++
      isReturnOnly = false
    }

    fun returnType() {
      isReturnOnly = segments == 0
      segments++
    }

    @NlsSafe
    fun render(): String = sb.toString()
  }
}
