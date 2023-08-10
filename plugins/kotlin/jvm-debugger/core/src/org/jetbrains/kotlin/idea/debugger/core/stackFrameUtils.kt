// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.core

import com.sun.jdi.LocalVariable
import com.sun.jdi.Location
import com.sun.jdi.Method
import org.jetbrains.kotlin.idea.debugger.base.util.DexDebugFacility
import org.jetbrains.kotlin.idea.debugger.base.util.safeVariables
import org.jetbrains.kotlin.idea.debugger.core.DebuggerUtils.getBorders

// A pair of a [LocalVariable] with its starting [Location] and
// a stable [Comparable] implementation.
//
// [LocalVariable.compareTo] orders variables by location and slot.
// The location is unstable on dex VMs due to spilling and the slot
// depends on compiler internals. This class orders variables
// according to the attached location and name instead.
class VariableWithLocation(
    val variable: LocalVariable,
    val location: Location,
) : Comparable<VariableWithLocation> {
    override fun compareTo(other: VariableWithLocation): Int =
        location.compareTo(other.location).takeIf { it != 0 }
            ?: name.compareTo(other.name)

    val name: String
        get() = variable.name()

    override fun toString(): String = "$name at $location"
}

// Returns a list of all [LocalVariable]s in the given methods with their starting
// location, ordered according to start location and variable name.
//
// The computed start locations take variable spilling into account on a dex VM.
// On a dex VM all variables are kept in registers and may have to be spilled
// during register allocation. This means that the same source level variable may
// correspond to several variables with different locations and slots.
// This method implements a heuristic to assign each visible variable to its
// actual starting location.
//
// ---
//
// This heuristic is not perfect. During register allocation we sometimes have
// to insert additional move instructions in the middle of a method, which can
// lead to large gaps in the scope of a local variable. Even if there is no additional
// spill code, the compiler is free to reorder blocks which can also create gaps
// in the variable live ranges. Unfortunately there is no way to detect this situation
// based on the local variable table and we will end up with the wrong variable order.
fun Method.sortedVariablesWithLocation(): List<VariableWithLocation> {
    val allVariables = safeVariables()
        ?: return emptyList()

    // On the JVM we can use the variable offsets directly.
    if (!DexDebugFacility.isDex(virtualMachine())) {
        return allVariables.mapNotNull { local ->
            local.getBorders()?.let { VariableWithLocation(local, it.start) }
        }.sorted()
    }

    // On dex, there are no separate slots for local variables. Instead, local variables
    // are kept in registers and are subject to spilling. When a variable is spilled,
    // its start offset is reset. In order to sort variables by introduction order,
    // we need to identify spilled variables.
    //
    // The heuristic we use for this is to look for pairs of variables with the same
    // name and type for which one begins exactly when the other ends.
    val startOffsets = mutableMapOf<Long, MutableList<LocalVariable>>()
    val replacements = mutableMapOf<LocalVariable, LocalVariable>()
    for (variable in allVariables) {
        val startOffset = variable.getBorders()?.start ?: continue
        startOffsets.getOrPut(startOffset.codeIndex()) { mutableListOf() } += variable
    }
    for (variable in allVariables) {
        val endOffset = variable.getBorders()?.endInclusive ?: continue

        // Note that the endOffset is inclusive - it doesn't necessarily correspond to
        // any bytecode index - so the variable ends exactly one index later.
        val otherVariables = startOffsets[endOffset.codeIndex() + 1] ?: continue
        for (other in otherVariables) {
            if (variable.name() == other.name() &&
                variable.signature() == other.signature() &&
                variable != other)
            {
                replacements[other] = variable
                break
            }
        }
    }
    return allVariables.mapNotNull { variable ->
        var alias = variable
        while (true) {
            alias = replacements[alias] ?: break
        }

        if (variable != alias) {
            replacements[variable] = alias
        }

        alias.getBorders()?.let {
            VariableWithLocation(variable, it.start)
        }
    }.sorted()
}

// Given a list of variables returns a copy of the list without duplicate variable names,
// keeping only the last occurrence for each name.
//
// For Java, this kind of filtering is done in [StackFrame.visibleVariables], but for
// Kotlin this needs to be done separately for every (inline) stack frame.
fun filterRepeatedVariables(sortedVariables: List<LocalVariable>): List<LocalVariable> =
    sortedVariables.associateBy { it.name() }.values.toList()
