// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.debugger

import com.intellij.debugger.jdi.LocalVariableProxyImpl
import com.intellij.debugger.jdi.StackFrameProxyImpl
import com.sun.jdi.LocalVariable
import org.jetbrains.kotlin.codegen.AsmUtil
import org.jetbrains.kotlin.codegen.inline.INLINE_FUN_VAR_SUFFIX
import org.jetbrains.kotlin.codegen.inline.isFakeLocalVariableForInline
import org.jetbrains.kotlin.idea.debugger.DebuggerUtils.getBorders
import org.jetbrains.kotlin.load.java.JvmAbi.LOCAL_VARIABLE_NAME_PREFIX_INLINE_ARGUMENT
import org.jetbrains.kotlin.load.java.JvmAbi.LOCAL_VARIABLE_NAME_PREFIX_INLINE_FUNCTION

val INLINED_THIS_REGEX = run {
    val escapedName = Regex.escape(AsmUtil.INLINE_DECLARATION_SITE_THIS)
    val escapedSuffix = Regex.escape(INLINE_FUN_VAR_SUFFIX)
    Regex("^$escapedName(?:$escapedSuffix)*$")
}

/**
 * An inline function aware view of a JVM stack frame.
 *
 * Due to inline functions in Kotlin, a single JVM stack frame maps
 * to multiple Kotlin stack frames. The Kotlin compiler emits metadata
 * in the form of local variables in order to allow the debugger to
 * invert this mapping.
 *
 * This class encapsulates the mapping from visible variables in a
 * JVM stack frame to a list of Kotlin stack frames and strips away
 * the additional metadata that is only used for disambiguating inline
 * stack frames.
 */
class InlineStackFrameVariableHolder private constructor(
    // Visible variables sorted by start offset. May contain duplicate variable names.
    private val sortedVariables: List<LocalVariableProxyImpl>,
    // Map from variable index to frame id. Every frame corresponds to a call to
    // a Kotlin (inline) function.
    private val variableFrameIds: IntArray,
    private val currentFrameId: Int,
) {
    // Returns all variables which are visible in the scope of the current (inline) function.
    val visibleVariables: List<LocalVariableProxyImpl>
        get() {
            val variables = mutableListOf<LocalVariableProxyImpl>()
            val variableNames = mutableSetOf<String>()
            for ((index, variable) in sortedVariables.withIndex().reversed()) {
                if (variableFrameIds[index] != currentFrameId)
                    continue

                if (variable.name() !in variableNames) {
                    variables += variable
                    variableNames += variable.name()
                }
            }
            return variables.reversed()
        }

    val parentFrame: InlineStackFrameVariableHolder?
        get() {
            val scopeVariableIndex = sortedVariables.indexOfLast {
                isFakeLocalVariableForInline(it.name())
            }
            if (scopeVariableIndex < 0) {
                return null
            }

            val parentSortedVariables = sortedVariables.subList(0, scopeVariableIndex)
            val parentVariableFrameIds = variableFrameIds.sliceArray(0 until scopeVariableIndex)
            val parentFrameId = parentSortedVariables.indexOfLast {
                isFakeLocalVariableForInline(it.name())
            }.takeIf { it >= 0 }?.let { variableFrameIds[it] } ?: 0
            return InlineStackFrameVariableHolder(parentSortedVariables, parentVariableFrameIds, parentFrameId)
        }

    companion object {
        // Constructs an inline stack frame from a list of currently visible variables
        // in introduction order.
        //
        // In order to construct the inline stack frame we need to associate each variable
        // with a call to an inline function (frameId) and determine (the frameId of) the
        // currently active inline function. Consider the following code.
        //
        //   fun f() {
        //       val x = 0
        //       g {
        //           h(2)
        //       }
        //   }
        //
        //   inline fun g(block: () -> Unit) {
        //       var y = 1
        //       block()
        //   }
        //
        //   inline fun h(a: Int) {
        //       var z = 3
        //       /* breakpoint */ ...
        //   }
        //
        // When stopped at the breakpoint in `h`, we have the following visible variables.
        //
        //   |      Variable     | Depth | Scope | Frame Id |
        //   |-------------------|-------|-------|----------|
        //   | x                 |     0 |     f |        0 |
        //   | $i$f$g            |     1 |     g |        1 |
        //   | y$iv              |     1 |     g |        1 |
        //   | $i$a$-g-Class$f$1 |     0 |   f$1 |        0 |
        //   | a$iv              |     1 |     h |        2 |
        //   | $i$f$h            |     1 |     h |        2 |
        //   | z$iv              |     1 |     h |        2 |
        //
        // There are two kinds of variables. Scope introduction variables are prefixed with
        // $i$f or $i$a and represent calls to inline functions or calls to function arguments
        // of inline functions respectively. All remaining variables represent source code
        // variables along with an inline depth represented by the number of `$iv` suffixes.
        //
        // This function works by iterating over the variables in introduction order with
        // a list of currently active stack frames. New frames are introduced or removed
        // when encountering a scope introduction variable. Each variable encountered
        // is either associated to one of the currently active stack frames or to the next
        // inline function call (since the arguments of inline functions appear before the
        // corresponding scope introduction variable).
        private fun fromSortedVisibleVariables(sortedVariables: List<LocalVariableProxyImpl>): InlineStackFrameVariableHolder {
            // Map from variables to frame ids
            val variableFrameIds = IntArray(sortedVariables.size)
            // Stack of currently active frames
            var activeFrames = mutableListOf(0)
            // Indices of variables representing arguments to the next function call
            val pendingVariables = mutableListOf<Int>()
            // Next unused frame id
            var nextFrameId = 1

            for ((currentIndex, variable) in sortedVariables.withIndex()) {
                val name = variable.name()
                val depth = getInlineDepth(name)
                when {
                    // When we encounter a call to an inline function, we start a new frame
                    // using the next free frameId and assign this frame to both the scope
                    // introduction variable as well as all pending variables.
                    name.startsWith(LOCAL_VARIABLE_NAME_PREFIX_INLINE_FUNCTION) -> {
                        val frameId = nextFrameId++
                        activeFrames.add(frameId)
                        for (pending in pendingVariables) {
                            variableFrameIds[pending] = frameId
                        }
                        pendingVariables.clear()
                        variableFrameIds[currentIndex] = frameId
                    }
                    // When we encounter a call to an inline function argument, we are
                    // moving up the call stack up to the depth of the function argument.
                    // This is why there should not be any pending variables at this
                    // point, since arguments to an inline function argument would be
                    // associated with a previous active frame.
                    //
                    // If we do encounter pending variables, then this indicates that the
                    // debug information is inconsistent and we mark all pending variables
                    // as invalid.
                    name.startsWith(LOCAL_VARIABLE_NAME_PREFIX_INLINE_ARGUMENT) -> {
                        for (pending in pendingVariables) {
                            variableFrameIds[pending] = -1
                        }
                        pendingVariables.clear()
                        if (depth + 1 < activeFrames.size) {
                            activeFrames = activeFrames.subList(0, depth + 1)
                        }
                        variableFrameIds[currentIndex] = activeFrames.last()
                    }
                    // Process variables in the current frame or a previous frame (for
                    // arguments to an inline function argument).
                    depth < activeFrames.size -> {
                        variableFrameIds[currentIndex] = activeFrames[depth]
                    }
                    // Process arguments to the next inline function call.
                    else -> {
                        if (depth == activeFrames.size) {
                            pendingVariables += currentIndex
                        } else {
                            // This can only happen if the debug information is invalid. In
                            // particular, the current index is not in scope and can be hidden.
                            variableFrameIds[currentIndex] = -1
                        }
                    }
                }
            }

            return InlineStackFrameVariableHolder(sortedVariables, variableFrameIds, activeFrames.last())
        }

        fun fromStackFrame(frame: StackFrameProxyImpl): InlineStackFrameVariableHolder {
            val allVariables = if (frame.virtualMachine.virtualMachine.isDexDebug()) {
                frame.location().method().safeVariables()
            } else null

            // On the JVM the variable start offsets correspond to the introduction order,
            // so we can proceed directly.
            if (allVariables == null) {
                val sortedVariables = frame.allVisibleVariables().sortedBy { it.variable }
                return fromSortedVisibleVariables(sortedVariables)
            }

            // On dex, there are no separate slots for local variables. Instead, local variables
            // are kept in registers and are subject to spilling. When a variable is spilled,
            // its start offset is reset. In order to sort variables by introduction order,
            // we need to identify spilled variables.
            //
            // The heuristic we use for this is to look for pairs of variables with the same
            // name and type for which one begins exactly one instruction after the other ends.
            //
            // Unfortunately, this needs access to the private [scopeStart] and [scopeEnd] fields
            // in [LocationImpl], but this is the only way to obtain the information we need.
            val startOffsets = mutableMapOf<Long, MutableList<LocalVariable>>()
            val replacements = mutableMapOf<LocalVariable, LocalVariable>()
            for (variable in allVariables) {
                val startOffset = variable.getBorders()?.start ?: continue
                startOffsets.computeIfAbsent(startOffset.codeIndex()) { mutableListOf() } += variable
            }
            for (variable in allVariables) {
                val endOffset = variable.getBorders()?.endInclusive ?: continue
                val otherVariables = startOffsets[endOffset.codeIndex() + 1] ?: continue
                for (other in otherVariables) {
                    if (variable.name() == other.name() && variable.type() == other.type()) {
                        replacements[other] = variable
                    }
                }
            }

            // Replace each visible variable by its first visible alias when sorting.
            val sortedVariables = frame.allVisibleVariables().sortedBy { proxy ->
                var variable = proxy.variable
                while (true) { variable = replacements[variable] ?: break }
                variable
            }

            return fromSortedVisibleVariables(sortedVariables)
        }

        // Returns a list of all visible variables without shadowing.
        private fun StackFrameProxyImpl.allVisibleVariables(): List<LocalVariableProxyImpl> =
            location().method().safeVariables()?.mapNotNull { variable ->
                if (variable.isVisible(stackFrame)) LocalVariableProxyImpl(this, variable) else null
            } ?: listOf()
    }
}

// Compute the current inline depth given a list of visible variables.
// All usages of this function should probably use [InlineStackFrame] instead,
// since the inline depth does not suffice to determine which variables
// are visible and this function will not work on a dex VM.
fun getInlineDepth(variables: List<LocalVariableProxyImpl>): Int {
    val rawInlineFunDepth = variables.count { it.name().startsWith(LOCAL_VARIABLE_NAME_PREFIX_INLINE_FUNCTION) }

    for (variable in variables.sortedByDescending { it.variable }) {
        val name = variable.name()
        val depth = getInlineDepth(name)
        if (depth > 0) {
            return depth
        } else if (name.startsWith(LOCAL_VARIABLE_NAME_PREFIX_INLINE_ARGUMENT)) {
            return 0
        }
    }

    return rawInlineFunDepth
}

fun getInlineDepth(variableName: String): Int {
    var endIndex = variableName.length
    var depth = 0

    val suffixLen = INLINE_FUN_VAR_SUFFIX.length
    while (endIndex >= suffixLen) {
        if (variableName.substring(endIndex - suffixLen, endIndex) != INLINE_FUN_VAR_SUFFIX) {
            break
        }

        depth++
        endIndex -= suffixLen
    }

    return depth
}

fun dropInlineSuffix(name: String): String {
    val depth = getInlineDepth(name)
    if (depth == 0) {
        return name
    }

    return name.dropLast(depth * INLINE_FUN_VAR_SUFFIX.length)
}