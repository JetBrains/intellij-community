// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.types

import com.jetbrains.python.inspections.PyInspectionMessages.CodifiedParam
import com.jetbrains.python.inspections.PyInspectionMessages.ProblemMessage
import org.jetbrains.annotations.ApiStatus

/**
 * A node in the tree explaining *why* a type mismatch was reported.
 *
 * The tree is produced on demand by [PyTypeChecker.explainMismatch] only when a type error is about
 * to be reported (it is never gathered during normal matching). Each node carries a single,
 * already-localized line as a [ProblemMessage] — a plain-text [ProblemMessage.description] for tests
 * and the Problems view, paired with a rich [ProblemMessage.tooltip] in which type and class
 * references are highlighted and clickable. [children] hold the nested reasons, e.g.
 *
 * ```
 * "C" is incompatible with protocol "A"      <- root
 *   "a" is an incompatible type              <- child
 *     "str" is not assignable to "int"       <- grandchild (leaf)
 * ```
 *
 * [step], when present, is a structured description of *what kind* of edge this node is (a protocol
 * check, an attribute or type-argument descent, a nominal leaf, a variance failure, …). It carries the
 * already-codified pieces the failure is about, so a renderer can recompose them into prose — folding a
 * chain of single-child descents into one access-path sentence, and adding the educational clause a
 * variance failure needs — instead of showing one indented line per node. It is purely presentational
 * metadata: [message] remains the source of truth, and any node without a [step] still renders from it.
 */
@ApiStatus.Internal
class PyTypeMismatchExplanation(
  val message: ProblemMessage,
  val children: List<PyTypeMismatchExplanation> = emptyList(),
  val step: PyMismatchStep? = null,
)

/**
 * Structured metadata for a [PyTypeMismatchExplanation] node, describing the semantic role of the edge so
 * a prose renderer can recompose the failure into readable sentences (see [PyTypeMismatchExplanation.step]).
 *
 * The variants split into three groups:
 *  - **descents** ([Protocol], [Attribute], [TypeArgument], [Return]) — a step *into* a nested position; a
 *    single-child chain of these folds into one access path (e.g. `inner.value`);
 *  - **terminals** ([Nominal], [Missing], [Invariant], [ContravariantParameter]) — the reason the descent
 *    bottomed out, rendered as the sentence's predicate;
 *  - **branches** ([NoUnionMember]) — a point where several parallel reasons apply, each kept on its own line.
 *
 * Every type/class piece is a pre-rendered [CodifiedParam] (plain description + clickable-HTML tooltip), so the
 * renderer never re-resolves types: it only arranges the pieces into prose.
 */
@ApiStatus.Internal
sealed interface PyMismatchStep {
  /**
   * The protocol-conformance frame; [subject] is the type whose members are being checked, [expected] the protocol
   * it is checked against. [expected] is used only to name the member in a union arm (where several protocols are
   * tried and the reader must know which one each line is about); a lone protocol mismatch ignores it (the headline
   * already names the protocol). Adds no path segment.
   */
  data class Protocol(val subject: CodifiedParam, val expected: CodifiedParam? = null) : PyMismatchStep

  /**
   * Descent into the attribute/member named [name]; folds into the access path as `.name`. [actual]/[expected]
   * are the attribute's own types (the subclass's vs the protocol's); the renderer reports them directly when the
   * mismatch is *inside* a parameterized attribute type, rather than descending into a meaningless `[1][1]` of
   * type-argument positions.
   */
  data class Attribute(
    val name: String,
    val actual: CodifiedParam? = null,
    val expected: CodifiedParam? = null,
  ) : PyMismatchStep

  /**
   * Descent into the type argument at [oneBasedIndex]. [actual]/[expected] are the argument's types at this level
   * (e.g. `set[None]` vs `set[int]` for the element of a `list`), so the renderer can drill in with a readable
   * "expected …, got …" line per level instead of a meaningless `[1][1]` position path.
   */
  data class TypeArgument(
    val oneBasedIndex: Int,
    val actual: CodifiedParam? = null,
    val expected: CodifiedParam? = null,
  ) : PyMismatchStep

  /** Descent into a callable's return type. */
  object Return : PyMismatchStep

  /** Terminal: [actual] is not assignable to [expected]. */
  data class Nominal(val actual: CodifiedParam, val expected: CodifiedParam) : PyMismatchStep

  /** Terminal: the member named [name] is absent on the actual type. [isMethod] picks the noun — "method" for a
   *  callable protocol member, "attribute" otherwise — so the message reads "has no method `f`" / "has no attribute `x`". */
  data class Missing(val name: String, val isMethod: Boolean = false) : PyMismatchStep

  /**
   * Terminal, educational: an invariant type parameter ([typeVar] of [owner]) rejects [actual] where
   * [expected] is required. Recorded only on the "surprising" path where [actual] *is* a subtype of
   * [expected], so the renderer can add the anti-surprise "even though … is a subtype of …" clause.
   *
   * [readOnlyAlternative], when set, is the name of a covariant read-only supertype the reader could annotate
   * the target with instead (e.g. `Sequence` for a `list`, `Mapping` for a `dict`), which sidesteps the
   * invariance; the renderer appends a "use `Sequence` if you only read from it" hint.
   */
  data class Invariant(
    val typeVar: String,
    val owner: CodifiedParam,
    val actual: CodifiedParam,
    val expected: CodifiedParam,
    val readOnlyAlternative: String? = null,
  ) : PyMismatchStep

  /**
   * Terminal: a TypedDict key mismatch. [owner] is the expected TypedDict type; [key] is the offending key;
   * [problem] says how it failed. [actual] and [expected] carry the key's value types for
   * [TypedDictKeyProblem.VALUE_TYPE] (null for the other kinds, which are about presence/qualifiers, not types).
   */
  data class TypedDictKey(
    val owner: CodifiedParam,
    val key: String,
    val problem: TypedDictKeyProblem,
    val actual: CodifiedParam?,
    val expected: CodifiedParam?,
  ) : PyMismatchStep

  /**
   * A transparent grouping of several independent top-level reasons (e.g. a callable that is wrong in both a
   * parameter and its return type). The renderer emits the children directly, with no line of its own — it
   * only exists so [PyTypeChecker.explainMismatch] can return a single root when the failure has several.
   */
  object Combined : PyMismatchStep

  /**
   * Terminal, educational: a contravariant callable parameter is too narrow — it must accept everything the
   * caller might pass ([required]) but only accepts [provided]. Identified by [name] when it has one, otherwise
   * by its 1-based position [oneBasedIndex] (parameters of a `Callable[[…], …]` are positional and unnamed).
   */
  data class ContravariantParameter(
    val name: String?,
    val oneBasedIndex: Int?,
    val required: CodifiedParam,
    val provided: CodifiedParam,
  ) : PyMismatchStep

  /**
   * Branch: [actual] matches no member of the union/protocol set [union]; each member's reason is a child.
   *
   * [actualIsUnion] tells the two directions this frame is recorded in apart. When `false` (the default), the
   * REQUIRED side is the union — [actual] (a single type) is assignable to no member of [union] — so a call-site
   * renderer reads "needs `x` to match `A | B`, but `arg` is `C`, which matches no member". When `true`, it is the
   * PROVIDED side that is a union — [actual] is the union value, [union] is the single required type each member
   * failed against — so "which matches no member" (of a non-union required type) would be nonsense; the renderer
   * must instead name the required type plainly and list the failing members as arms.
   */
  data class NoUnionMember(val actual: CodifiedParam, val union: CodifiedParam, val actualIsUnion: Boolean = false) : PyMismatchStep

  /**
   * A per-member wrapper under a [NoUnionMember] branch, naming the failing union [member] so several
   * otherwise-indistinguishable reasons can be told apart (e.g. two callables that differ only by a parameter name).
   * The prose renderer drops this wrapper and keeps a single member-named arm when the wrapped reason already
   * self-names (a missing/wrong-typed protocol attribute), and keeps the wrapper line above the reason otherwise.
   */
  data class UnionMember(val member: CodifiedParam) : PyMismatchStep
}

/** How a TypedDict key failed to match (see [PyMismatchStep.TypedDictKey]). */
@ApiStatus.Internal
enum class TypedDictKeyProblem {
  /** The key is absent on the actual TypedDict. */
  MISSING,

  /** The key's value type is incompatible (assignability, or mutable-key invariance). */
  VALUE_TYPE,

  /** A mutable key is required, but the actual key is read-only. */
  READONLY,

  /** The key's required/not-required qualifier is inconsistent. */
  REQUIRED,
}
