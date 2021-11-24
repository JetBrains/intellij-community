// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.debugger.stackFrame

import com.intellij.debugger.jdi.LocalVariableProxyImpl
import com.intellij.debugger.jdi.StackFrameProxyImpl
import com.intellij.xdebugger.frame.XStackFrame
import com.sun.jdi.Location
import com.sun.jdi.Method
import org.jetbrains.kotlin.idea.debugger.*
import org.jetbrains.kotlin.idea.debugger.DebuggerUtils.getBorders
import org.jetbrains.kotlin.load.java.JvmAbi

object InlineStackTraceCalculator {
    fun calculateInlineStackTrace(frameProxy: StackFrameProxyImpl): List<XStackFrame> {
        val location = frameProxy.safeLocation() ?: return emptyList()
        val method = location.safeMethod() ?: return emptyList()
        val inlineStackFramesInfo = method.getInlineStackFramesInfo(location)
        if (inlineStackFramesInfo.isEmpty()) {
            return emptyList()
        }

        val allLocations = method.safeAllLineLocations()
        if (allLocations.isEmpty()) {
            return emptyList()
        }

        inlineStackFramesInfo.fetchDepths()
        val newFrameProxyLocation = inlineStackFramesInfo.fetchLocationsAndGetNewFrameProxyLocation(allLocations, location)
        return createInlineStackFrames(inlineStackFramesInfo, frameProxy, newFrameProxyLocation)
    }

    private fun createInlineStackFrames(
        inlineStackFramesInfo: List<InlineStackFrameInfo>,
        frameProxy: StackFrameProxyImpl,
        frameProxyLocation: Location
    ): MutableList<KotlinStackFrame> {
        val stackFrames = mutableListOf<KotlinStackFrame>()
        var variableHolder: InlineStackFrameVariableHolder? =
            InlineStackFrameVariableHolder.fromStackFrame(frameProxy)
        for (info in inlineStackFramesInfo.asReversed()) {
            stackFrames.add(
                info.toInlineStackFrame(
                    frameProxy,
                    variableHolder.getVisibleVariables()
                )
            )
            variableHolder = variableHolder?.parentFrame
        }
        val originalFrame = KotlinStackFrameWithProvidedVariables(
            safeInlineStackFrameProxy(frameProxyLocation, 0, frameProxy),
            variableHolder.getVisibleVariables()
        )
        stackFrames.add(originalFrame)

        return stackFrames
    }

    private fun Method.getInlineStackFramesInfo(location: Location) =
        getInlineFunctionInfos()
            .filter { it.contains(location) }
            .sortedBy { it.borders.start }
            .map { InlineStackFrameInfo(it, location, 0) }

    private fun Method.getInlineFunctionInfos(): List<AbstractInlineFunctionInfo> {
        val localVariables = safeVariables() ?: return emptyList()
        val inlineFunctionInfos = mutableListOf<AbstractInlineFunctionInfo>()
        for (variable in localVariables) {
            val borders = variable.getBorders() ?: continue
            val variableName = variable.name()
            if (variableName.startsWith(JvmAbi.LOCAL_VARIABLE_NAME_PREFIX_INLINE_FUNCTION)) {
                inlineFunctionInfos.add(InlineFunctionInfo(variableName, borders))
            } else if (variableName.startsWith(JvmAbi.LOCAL_VARIABLE_NAME_PREFIX_INLINE_ARGUMENT)) {
                inlineFunctionInfos.add(InlineLambdaInfo(variableName, borders))
            }
        }

        return inlineFunctionInfos
    }

    // Consider the following example:
    //     inline fun baz() = 1 // Breakpoint
    //     inline fun bar(f: () -> Unit) = f()
    //     inline fun foo(f: () -> Unit) = bar { f() }
    //     fun main() = foo { baz() }
    // The depth of an inline lambda is equal to the depth of a method where it was declared.
    // Inline function infos should be listed according to the function call order,
    // so that depths of inline lambdas could be calculated correctly.
    // The method will produce the following depths:
    //     foo                       -> 1
    //     bar                       -> 2
    //     $i$a$-bar-MainKt$foo$1$iv -> 1
    //     $i$a$-foo-MainKt$main$1   -> 0
    //     baz                       -> 1
    private fun List<InlineStackFrameInfo>.fetchDepths() {
        var currentDepth = 0
        val depths = mutableMapOf<String, Int>()
        for (inlineStackFrameInfo in this) {
            val inlineFunctionInfo = inlineStackFrameInfo.inlineFunctionInfo
            val calculatedDepth =
                if (inlineFunctionInfo is InlineLambdaInfo) {
                    inlineFunctionInfo.getDeclarationFunctionName()
                        ?.let { depths[it] }
                        ?: 0 // The lambda was declared in the top frame
                } else {
                    currentDepth + 1
                }
            depths[inlineFunctionInfo.name] = calculatedDepth
            inlineStackFrameInfo.depth = calculatedDepth
            currentDepth = calculatedDepth
        }
    }

    private fun List<InlineStackFrameInfo>.fetchLocationsAndGetNewFrameProxyLocation(
        allLocations: List<Location>,
        originalLocation: Location
    ): Location {
        val iterator = allLocations.iterator()
        val newFrameProxyLocation =
            iterator.getInlineFunctionCallLocation(first().inlineFunctionInfo)
                ?: originalLocation
        var inlineStackFrameIndex = 1
        var prevLocation = originalLocation
        for (location in iterator) {
            if (inlineStackFrameIndex > lastIndex) {
                break
            }
            if (this[inlineStackFrameIndex].inlineFunctionInfo.contains(location)) {
                this[inlineStackFrameIndex - 1].location = prevLocation
                inlineStackFrameIndex++
            }
            prevLocation = location
        }
        last().location = originalLocation

        return newFrameProxyLocation
    }

    private fun Iterator<Location>.getInlineFunctionCallLocation(inlineFunctionInfo: AbstractInlineFunctionInfo): Location? {
        var prevLocation: Location? = null
        for (location in this) {
            if (inlineFunctionInfo.contains(location)) {
                return prevLocation
            }
            prevLocation = location
        }

        return null
    }

    private fun InlineStackFrameVariableHolder?.getVisibleVariables() =
        this?.visibleVariables.orEmpty()
}

private data class InlineStackFrameInfo(val inlineFunctionInfo: AbstractInlineFunctionInfo, var location: Location, var depth: Int) {
    fun toInlineStackFrame(
        frameProxy: StackFrameProxyImpl,
        visibleVariables: List<LocalVariableProxyImpl>
    ) =
        InlineStackFrame(
            location,
            inlineFunctionInfo.getDisplayName(),
            frameProxy,
            depth,
            visibleVariables
        )
}

private abstract class AbstractInlineFunctionInfo(val name: String, val borders: ClosedRange<Location>) {
    abstract fun getDisplayName(): String

    fun contains(location: Location) =
        location in borders
}

private class InlineFunctionInfo(
    name: String,
    borders: ClosedRange<Location>
) : AbstractInlineFunctionInfo(
        name.substringAfter(JvmAbi.LOCAL_VARIABLE_NAME_PREFIX_INLINE_FUNCTION),
        borders
) {
    override fun getDisplayName() = name
}

private class InlineLambdaNameWrapper(name: String) {
    companion object {
        private val inlineLambdaRegex =
            "${JvmAbi.LOCAL_VARIABLE_NAME_PREFIX_INLINE_ARGUMENT.replace("$", "\\$")}-(.+)-[^\$]+\\$([^\$]+)\\$.*"
                .toRegex()
    }

    private val groupValues = inlineLambdaRegex.matchEntire(name)?.groupValues
    val lambdaName = groupValues?.getOrNull(1)
    val declarationFunctionName = groupValues?.getOrNull(2)

    fun isValid() =
        groupValues != null
}

private class InlineLambdaInfo(name: String, borders: ClosedRange<Location>) : AbstractInlineFunctionInfo(name, borders) {
    private val nameWrapper = InlineLambdaNameWrapper(name)

    override fun getDisplayName(): String {
        if (!nameWrapper.isValid()) {
            return name
        }
        return "lambda '${nameWrapper.lambdaName}' in '${nameWrapper.declarationFunctionName}'"
    }

    fun getDeclarationFunctionName(): String? =
        nameWrapper.declarationFunctionName
}
