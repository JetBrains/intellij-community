// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.core.stackFrame

import com.intellij.debugger.impl.DebuggerUtilsEx
import com.intellij.debugger.jdi.LocalVariableProxyImpl
import com.intellij.debugger.jdi.StackFrameProxyImpl
import com.intellij.debugger.ui.impl.watch.StackFrameDescriptorImpl
import com.intellij.xdebugger.frame.XStackFrame
import com.sun.jdi.LocalVariable
import com.sun.jdi.Location
import com.sun.jdi.Method
import com.sun.jdi.StackFrame
import org.jetbrains.kotlin.codegen.inline.*
import org.jetbrains.kotlin.idea.debugger.base.util.*
import org.jetbrains.kotlin.idea.debugger.base.util.getInlineDepth
import org.jetbrains.kotlin.idea.debugger.base.util.safeLineNumber
import org.jetbrains.kotlin.idea.debugger.base.util.safeMethod
import org.jetbrains.kotlin.idea.debugger.base.util.safeSourceName
import org.jetbrains.kotlin.idea.debugger.core.*
import org.jetbrains.kotlin.load.java.JvmAbi

object InlineStackTraceCalculator {
    fun calculateInlineStackTrace(descriptor: StackFrameDescriptorImpl): List<XStackFrame> =
        descriptor.frameProxy.stackFrame.computeKotlinStackFrameInfos().map {
            it.toXStackFrame(descriptor)
        }.reversed()

    // Calculate the variables that are visible in the top stack frame.
    fun calculateVisibleVariables(frameProxy: StackFrameProxyImpl): List<LocalVariableProxyImpl> {
        // This is called from CoroutineStackFrame even for Java frames. We only need to compute for Kotlin frames.
        return if (frameProxy.location().isInKotlinSources()) {
            frameProxy.stackFrame.computeKotlinStackFrameInfos()
                .lastOrNull()?.visibleVariableProxies(frameProxy) ?: emptyList()
        } else {
            frameProxy.safeVisibleVariables()
        }
    }
}

private val INLINE_LAMBDA_REGEX =
    "${Regex.escape(JvmAbi.LOCAL_VARIABLE_NAME_PREFIX_INLINE_ARGUMENT)}-(.+)-[^\$]+\\$([^\$]+)\\$.*"
        .toRegex()

data class LambdaName(val inlineFunctionName: String, val declarationFunctionName: String)

class KotlinStackFrameInfo(
    // The scope introduction variable for an inline stack frame, i.e., $i$f$function
    // or $i$a$lambda. The name of this variable determines the label for an inline
    // stack frame. This is null for non-inline stack frames.
    val scopeVariable: VariableWithLocation?,
    // For inline lambda stack frames we need to include the visible variables from the
    // enclosing stack frame.
    var enclosingStackFrame: KotlinStackFrameInfo?,
    // All variables that were added in this stack frame.
    val visibleVariablesWithLocations: MutableList<VariableWithLocation>,
    // For an inline stack frame, the number of calls from the nearest non-inline function.
    // TODO: Remove. This is only used in the evaluator, to look up variables, but the depth
    //       is not sufficient to determine which frame a variable is in.
    val depth: Int,
) {
    // The location for this stack frame, i.e., the current location of the StackFrame for the
    // most recent frame or the location of an inline function call for any enclosing frame.
    // This depends on the next stack frame info and is initialized after the KotlinStackFrameInfo.
    var callLocation: Location? = null

    var inlineScopeNumber = -1
    var surroundingScopeNumber = -1

    val displayName: String?
        get() {
            val scopeVariableName = scopeVariable?.name?.dropInlineScopeInfo() ?: return null
            if (scopeVariableName.startsWith(JvmAbi.LOCAL_VARIABLE_NAME_PREFIX_INLINE_FUNCTION)) {
                return scopeVariableName.substringAfter(JvmAbi.LOCAL_VARIABLE_NAME_PREFIX_INLINE_FUNCTION)
            }

            lambdaDetails?.let {
                return "lambda '${it.inlineFunctionName}' in '${it.declarationFunctionName}'"
            }

            return scopeVariableName
        }

    val lambdaDetails: LambdaName?
        get() {
            val scopeVariableName = scopeVariable?.name ?: return null
            val match = INLINE_LAMBDA_REGEX.matchEntire(scopeVariableName) ?: return null
            val (inlineFunctionName, declarationFunctionName) = match.destructured
            return LambdaName(inlineFunctionName, declarationFunctionName)
        }

    val visibleVariables: List<LocalVariable>
        get() = filterRepeatedVariables(
            visibleVariablesWithLocations
                .mapTo(enclosingStackFrame?.visibleVariables?.toMutableList() ?: mutableListOf()) {
                    it.variable
                }
        )

    fun visibleVariableProxies(frameProxy: StackFrameProxyImpl): List<LocalVariableProxyImpl> =
        visibleVariables.map { LocalVariableProxyImpl(frameProxy, it) }

    fun toXStackFrame(descriptor: StackFrameDescriptorImpl): XStackFrame {
        val frameProxy = descriptor.frameProxy
        val variables = visibleVariableProxies(frameProxy)
        displayName?.let { name ->
            return InlineStackFrame(callLocation, name, frameProxy, depth, variables, inlineScopeNumber, surroundingScopeNumber)
        }

        // no need to create a new descriptor if the location is the same, also it breaks drop frame for now
        if (descriptor.location == callLocation) {
            return KotlinStackFrame(descriptor, variables)
        }

        val proxy = safeInlineStackFrameProxy(callLocation, 0, frameProxy, inlineScopeNumber, surroundingScopeNumber)
        return KotlinStackFrame(proxy, variables)
    }
}

fun StackFrame.computeKotlinStackFrameInfos(alwaysComputeCallLocations: Boolean = false): List<KotlinStackFrameInfo> {
    val location = location()
    val method = location.safeMethod() ?: return emptyList()

    val allVisibleVariables = method.sortedVariablesWithLocation().filter {
        it.variable.isVisible(this)
    }

    val stackFrameInfos = if (shouldComputeStackFrameInfosUsingTheOldScheme(allVisibleVariables) || alwaysComputeCallLocations) {
        computeStackFrameInfosWithCallLocations(location, method, allVisibleVariables)
    } else {
        computeStackFrameInfosUsingScopeNumbers(location, method, allVisibleVariables) ?:
            computeStackFrameInfosWithCallLocations(location, method, allVisibleVariables)
    }
    return stackFrameInfos
}

private fun computeStackFrameInfosWithCallLocations(
    location: Location,
    method: Method,
    allVisibleVariables: List<VariableWithLocation>
): List<KotlinStackFrameInfo> =
    computeStackFrameInfos(allVisibleVariables).also {
        fetchCallLocations(method, it, location)
    }


// Constructs a list of inline stack frames from a list of currently visible variables
// in introduction order.
//
// In order to construct the inline stack frame we need to associate each variable with
// a call to an inline function and keep track of the currently active inline function.
// Consider the following code.
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
//   |      Variable     | Depth | Scope |         Frames        |  Pending |
//   |-------------------|-------|-------|-----------------------|----------|
//   | x                 |     0 |     f |                 f:[x] |          |
//   | $i$f$g            |     1 |     g |           f:[x], g:[] |          |
//   | y$iv              |     1 |     g |       f:[x], g:[y$iv] |          |
//   | $i$a$-g-Class$f$1 |     0 |   f$1 |                 f:[x] |          |
//   | a$iv              |     1 |     h |                 f:[x] | 1:[a$iv] |
//   | $i$f$h            |     1 |     h |       f:[x], h:[a$iv] |          |
//   | z$iv              |     1 |     h | f:[x], h:[a$iv, z$iv] |          |
//
// There are two kinds of variables. Scope introduction variables are prefixed with
// $i$f or $i$a and represent calls to inline functions or calls to function arguments
// of inline functions respectively. All remaining variables represent source code
// variables along with an inline depth represented by the number of `$iv` suffixes.
//
// This function works by simulating the current active call stack while iterating
// over the variables in introduction order. New frames are introduced or removed
// when encountering scope introduction variables. Each variable encountered
// is either associated to the currently active stack frame or to the next
// stack frame at its depth (since arguments of inline functions appear before the
// corresponding scope introduction variable).
private fun computeStackFrameInfos(sortedVariables: List<VariableWithLocation>): List<KotlinStackFrameInfo> {
    // List of all stack frames in introduction order. We always start with a non-inline stack frame.
    val stackFrameInfos = mutableListOf(KotlinStackFrameInfo(null, null, mutableListOf(), 0))
    // Variables which should appear in a future stack frame. On the jvm these are arguments
    // to the next inline function call. On dex there are also variables which were moved
    // before or after their stack frame.
    val pendingVariables = mutableMapOf<Int, MutableList<VariableWithLocation>>()
    // The current call stack, as a list of active frame indices into stackFrameInfos. This is always non-empty.
    var activeFrames = mutableListOf(0)

    for (variable in sortedVariables) {
        // When we encounter a call to an inline function, we start a new stack frame
        // without any enclosing frame.
        if (variable.name.startsWith(JvmAbi.LOCAL_VARIABLE_NAME_PREFIX_INLINE_FUNCTION)) {
            val depth = activeFrames.size
            stackFrameInfos += KotlinStackFrameInfo(
                variable,
                null,
                pendingVariables[depth] ?: mutableListOf(),
                depth
            )
            pendingVariables.remove(depth)
            activeFrames += stackFrameInfos.size - 1
            continue
        }

        val depth = getInlineDepth(variable.name)
        when {
            // When we encounter a call to an inline function argument, we are
            // moving up the call stack to the depth of the function argument.
            variable.name.startsWith(JvmAbi.LOCAL_VARIABLE_NAME_PREFIX_INLINE_ARGUMENT) -> {
                // for lambda arguments to inline only functions the depth doesn't change,
                // i.e., we would have depth + 1 == activeFrames.size.
                if (depth + 1 < activeFrames.size) {
                    activeFrames = activeFrames.subList(0, depth + 1)
                }
                // TODO: If we get unlucky, we may encounter an inline lambda before its enclosing frame.
                //       There's no good workaround in this case, since we can't distinguish between
                //       inline function calls inside such a lambda and the calls corresponding to the
                //       enclosing frame. We should communicate this situation somehow.
                if (depth < activeFrames.size) {
                    stackFrameInfos += KotlinStackFrameInfo(
                        variable,
                        stackFrameInfos[activeFrames[depth]],
                        pendingVariables[depth] ?: mutableListOf(),
                        depth
                    )
                    pendingVariables.remove(depth)
                    activeFrames[depth] = stackFrameInfos.size - 1
                }
            }
            // Process variables in the current frame.
            depth == activeFrames.size - 1 -> {
                stackFrameInfos[activeFrames[depth]].visibleVariablesWithLocations += variable
            }
            // Process arguments to the next inline function call or variables that were
            // moved to the wrong location on dex.
            else -> {
                pendingVariables.getOrPut(depth) { mutableListOf() } += variable
            }
        }
    }

    // For valid debug information there should be no pending variables at this point,
    // except possibly when we are stopped while evaluating default arguments to an inline
    // function.
    for ((depth, variables) in pendingVariables.entries) {
        if (depth < activeFrames.size) {
            stackFrameInfos[activeFrames[depth]].visibleVariablesWithLocations += variables
        } else {
            // This can happen in two cases: Either we are stopped right before an inline call
            // when some arguments are already in variables or the debug information is invalid
            // and some variables were moved after a call to an inline lambda.
            // Instead of throwing them away we'll add these variables to the last call stack at
            // the desired depth (there probably won't be one in the first case).
            stackFrameInfos.lastOrNull {
                it.depth == depth
            }?.visibleVariablesWithLocations?.addAll(variables)
        }
    }

    return stackFrameInfos
}

// This function computes inline stack trace using scope numbers encoded in
// local variables. The format affects 3 kinds of variables:
//   1. Inline function marker variables
//      $i$f$name\[scope number]\[call site line number]
//   2. Inline lambda marker variables
//      $i$a$name\[scope number]\[call site line number]\[surrounding scope number]
//   3. Local variables from inline functions or lambdas
//      name\[scope number]
// If a variable has a scope number, then it should be visible in an inline frame that is
// represented by a marker variable with the same scope number. Inline lambda frames should
// also include variables from their surrounding scopes accordingly.
private fun computeStackFrameInfosUsingScopeNumbers(
    location: Location,
    method: Method,
    sortedVariables: List<VariableWithLocation>
): List<KotlinStackFrameInfo>? {
    val firstFrameVariables = mutableListOf<VariableWithLocation>()
    val firstFrameInfo = KotlinStackFrameInfo(null, null, firstFrameVariables, 0)
    val stackFrameInfos = mutableListOf(firstFrameInfo)

    val scopeNumberToVariables = mutableMapOf<Int, MutableList<VariableWithLocation>>()
    for (variable in sortedVariables) {
        val scopeNumber = variable.name.getInlineScopeInfo()?.scopeNumber
        if (scopeNumber != null) {
            scopeNumberToVariables.getOrPut(scopeNumber) { mutableListOf() }.add(variable)
        } else {
            firstFrameVariables.add(variable)
        }
    }

    val lineNumberToLocation = DebuggerUtilsEx.allLineLocations(method).orEmpty()
        .associateBy { it.lineNumber("Java") }

    val callSiteLocations = mutableListOf<Location?>()
    val infosAndSurroundingScopeIds = mutableListOf<Pair<KotlinStackFrameInfo, Int>>()
    val scopeNumberToFrameInfo = mutableMapOf<Int, KotlinStackFrameInfo>()
    scopeNumberToFrameInfo[0] = firstFrameInfo

    for (variable in sortedVariables) {
        val name = variable.name
        if (!JvmAbi.isFakeLocalVariableForInline(name)) {
            continue
        }

        val (scopeNumber, callSiteLineNumber, surroundingScopeNumber) = name.getInlineScopeInfo() ?: return null
        val frameInfo = KotlinStackFrameInfo(
            variable,
            null,
            scopeNumberToVariables[scopeNumber] ?: mutableListOf(),
            -1
        ).apply { inlineScopeNumber = scopeNumber }

        if (name.isInlineLambdaMarkerVariableName) {
            frameInfo.surroundingScopeNumber = surroundingScopeNumber ?: -1

            if (surroundingScopeNumber != null) {
                infosAndSurroundingScopeIds.add(Pair(frameInfo, surroundingScopeNumber))
            } else {
                frameInfo.enclosingStackFrame = firstFrameInfo
            }
        }

        scopeNumberToFrameInfo[scopeNumber] = frameInfo
        callSiteLocations += lineNumberToLocation[callSiteLineNumber]
        stackFrameInfos += frameInfo
    }

    // We may encounter a lambda before its surrounding lambda or function,
    // that's why we have to process enclosing stack frames after the variable
    // processing.
    for ((info, id) in infosAndSurroundingScopeIds) {
        info.enclosingStackFrame = scopeNumberToFrameInfo[id]
    }

    callSiteLocations += location
    for ((callSiteLocation, frameInfo) in callSiteLocations.zip(stackFrameInfos)) {
        frameInfo.callLocation = callSiteLocation
    }

    return stackFrameInfos
}

// Heuristic to guess inline call locations based on the variables in a `KotlinStackFrameInfo`
// and the `KotlinDebug` SMAP stratum.
//
// There is currently no way of reliably computing inline call locations using the Kotlin
// debug information. We can try to compute the call location based on the locations of the
// local variables in an inline stack frame or based on the KotlinDebug stratum for the
// one case where this will work.
//
// ---
//
// On the JVM it seems reasonable to determine the call location using the location
// immediately preceding a scope introduction variable.
//
// The JVM IR backend generates a line number before every inline call. For inline
// functions without default arguments the scope introduction variable starts at the first
// line of an inline function. In this particular case, the previous location corresponds
// to the call location. This is not true for the old JVM backend or for inline functions
// with default arguments.
//
// There is not much we can do for the old JVM backend, since there are cases where we simply
// do not have a line number for an inline call. We're ignoring this issue since the old
// backend is already deprecated.
//
// In order to handle inline functions with default arguments we could take the location
// immediately preceding all variables in the inline stack frame. This would include the function
// arguments. However, there still wouldn't be any guarantee that this corresponds to the call
// location, since the inliner does not introduce a new variable for inline function arguments
// if the argument at the call site is already in a variable. This is an optimization that
// always leads to invalid debug information and this case is not an exception.
//
// Finally, it is also not clear how to reliably determine whether we are in an inline default
// stub, so all in all it is probably better to just use the scope introduction variable and accept
// that the call location will be incorrect in inline default stubs.
//
// Finally, of course, on dex the situation is worse since we cannot rely on the locations
// of local variables due to spilling.
//
// ---
//
// The KotlinDebug stratum records the location of the first inline call in a function and
// can thus be used to determine the call location of the first inline stack frame. This
// information is produced by the inliner itself and should be correct.
//
// The only heuristic step is an issue with the API: We need to produce a JDI [Location],
// but the KotlinDebug SMAP only provides us with a file and line pair. This means that
// we actually don't know the code index of the call. OTOH, there is no real reason why
// the UI would need the code index at all and this is something we could fix by moving
// away from using JDI data structures for the UI representation.
//
// Note though, that the KotlinDebug stratum only records the location of the first call to
// an inline *function*, so it is useless for nested calls or calls to inline only functions.
// The latter result in calls to lambda arguments which don't appear in the KotlinDebug stratum.
//
// Moreover, the KotlinDebug stratum only applies to remapped line numbers, so it cannot be
// used in top-level inline lambda arguments.
private fun fetchCallLocations(
    method: Method,
    kotlinStackFrameInfos: List<KotlinStackFrameInfo>,
    defaultLocation: Location,
) {
    kotlinStackFrameInfos.lastOrNull()?.callLocation = defaultLocation

    // If there are no inline calls we don't need to fetch the line locations.
    // It's important to exit early here, since fetching line locations might involve
    // a round-trip to the debuggee vm.
    if (kotlinStackFrameInfos.size <= 1)
        return

    val allLocations = DebuggerUtilsEx.allLineLocations(method)
    if (allLocations == null) {
        // In case of broken debug information we use the same location for all stack frames.
        kotlinStackFrameInfos.forEach { it.callLocation = defaultLocation }
        return
    }

    // If the current location is covered by the KotlinDebug stratum then we can use it to
    // look up the location of the first call to an inline function (but not to an inline
    // lambda argument).
    var startIndex = 1
    kotlinStackFrameInfos[1].scopeVariable?.takeIf {
        it.name.startsWith(JvmAbi.LOCAL_VARIABLE_NAME_PREFIX_INLINE_FUNCTION)
    }?.let { firstInlineScopeVariable ->
        // We cannot use the current location to look up the location in the KotlinDebug
        // stratum, since it might be in a top-level inline lambda and hence not covered
        // by KotlinDebug.
        //
        // The scope introduction variable on the other hand should start inside an
        // inline function.
        val startOffset = firstInlineScopeVariable.location
        val callLineNumber = startOffset.safeLineNumber(KOTLIN_DEBUG_STRATA_NAME)
        val callSourceName = startOffset.safeSourceName(KOTLIN_DEBUG_STRATA_NAME)
        if (callLineNumber != -1 && callSourceName != null) {
            // Find the closest location to startOffset with the correct line number and source name.
            val callLocation = allLocations.lastOrNull { location ->
                location < startOffset &&
                        location.safeLineNumber() == callLineNumber &&
                        location.safeSourceName() == callSourceName
            }
            if (callLocation != null) {
                kotlinStackFrameInfos[0].callLocation = callLocation
                startIndex++
            }
        }
    }

    for (index in startIndex until kotlinStackFrameInfos.size) {
        // Find the index of the location closest to the start of the scope variable.
        // NOTE: [Method.allLineLocations] returns locations ordered by codeIndex.
        val scopeIndex =
            kotlinStackFrameInfos[index]
                .scopeVariable
                ?.location
                ?.let(allLocations::binarySearch)

        // If there is no scope introduction variable, or if it effectively starts on the first
        // location we use the default location. The latter case can only happen if the user
        // called a Kotlin inline function directly from Java. We will incorrectly present
        // an inline stack frame in this case, but this is not a supported use case anyway.
        val prev = kotlinStackFrameInfos[index - 1]
        if (scopeIndex == null || scopeIndex in -1..0) {
            prev.callLocation = defaultLocation
            continue
        }

        var locationIndex = if (scopeIndex > 0) {
            // If the scope variable starts on a line we take the previous line.
            scopeIndex - 1
        } else /* if (scopeIndex <= -2) */ {
            // If the returned location is < 0, then the element should be inserted at position
            // `-locationIndex - 1` to preserve the sorting order and the element at position
            // `-locationIndex - 2` contains the previous line number.
            -scopeIndex - 2
        }

        // Skip to the previous location if the call location lands on a synthetic fake.kt:1 line.
        // These lines are inserted by the compiler to force new line numbers for single line lambdas.
        if (isKotlinFakeLineNumber(allLocations[locationIndex])) {
            locationIndex = (locationIndex - 1).coerceAtLeast(0)
        }
        prev.callLocation = allLocations[locationIndex]
    }
}

private fun shouldComputeStackFrameInfosUsingTheOldScheme(variables: List<VariableWithLocation>): Boolean =
    variables.any {
        val name = it.name
        JvmAbi.isFakeLocalVariableForInline(name) && name.getInlineScopeInfo() == null
    }
