// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.types

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
 */
@ApiStatus.Internal
class PyTypeMismatchExplanation(
  val message: ProblemMessage,
  val children: List<PyTypeMismatchExplanation> = emptyList(),
)
