// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.j2k.post.processing.inference.common

import org.jetbrains.kotlin.K1Deprecation

@K1Deprecation
enum class ConstraintPriority {
    /* order of entries here used when solving system of constraints */
    SUPER_DECLARATION,
    INITIALIZER,
    RETURN,
    ASSIGNMENT,
    PARAMETER,
    RECEIVER_PARAMETER,
    COMPARE_WITH_NULL,
    USE_AS_RECEIVER,
}

@K1Deprecation
sealed class Constraint {
    abstract val priority: ConstraintPriority
}

@K1Deprecation
class SubtypeConstraint(
    var subtype: ConstraintBound,
    var supertype: ConstraintBound,
    override val priority: ConstraintPriority
) : Constraint() {
    operator fun component1() = subtype
    operator fun component2() = supertype
}

@K1Deprecation
class EqualsConstraint(
    var left: ConstraintBound,
    var right: ConstraintBound,
    override val priority: ConstraintPriority
) : Constraint() {
    operator fun component1() = left
    operator fun component2() = right
}

@K1Deprecation
fun Constraint.copy() = when (this) {
    is SubtypeConstraint -> SubtypeConstraint(subtype, supertype, priority)
    is EqualsConstraint -> EqualsConstraint(left, right, priority)
}

@K1Deprecation
sealed class ConstraintBound
@K1Deprecation
class TypeVariableBound(val typeVariable: TypeVariable) : ConstraintBound()
@K1Deprecation
class LiteralBound private constructor(val state: State) : ConstraintBound() {
    companion object {
        val UPPER = LiteralBound(State.UPPER)
        val LOWER = LiteralBound(State.LOWER)
        val UNKNOWN = LiteralBound(State.UNKNOWN)
    }
}

@K1Deprecation
fun State.constraintBound(): LiteralBound? = when (this) {
    State.LOWER -> LiteralBound.LOWER
    State.UPPER -> LiteralBound.UPPER
    State.UNKNOWN -> LiteralBound.UNKNOWN
    State.UNUSED -> null
}

@K1Deprecation
val ConstraintBound.isUnused
    get() = when (this) {
        is TypeVariableBound -> typeVariable.state == State.UNUSED
        is LiteralBound -> state == State.UNUSED
    }
