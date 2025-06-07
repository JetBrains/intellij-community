// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.coroutine.data

import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.JavaStackFrame
import com.intellij.debugger.engine.JavaValue
import com.intellij.debugger.impl.DebuggerContextImpl
import com.intellij.debugger.jdi.StackFrameProxyImpl
import com.intellij.debugger.ui.tree.render.DescriptorLabelListener
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ReadAction
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.frame.XCompositeNode
import com.intellij.xdebugger.frame.XValueChildrenList
import com.intellij.xdebugger.impl.frame.XDebuggerFramesList
import com.sun.jdi.Location
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.debugger.base.util.safeLocation
import org.jetbrains.kotlin.idea.debugger.core.stackFrame.InlineStackTraceCalculator
import org.jetbrains.kotlin.idea.debugger.core.stackFrame.KotlinStackFrame
import org.jetbrains.kotlin.idea.debugger.coroutine.KotlinDebuggerCoroutinesBundle
import org.jetbrains.kotlin.idea.debugger.coroutine.KotlinVariableNameFinder
import org.jetbrains.kotlin.idea.debugger.coroutine.proxy.safeCoroutineStackFrameProxy

/**
 * Coroutine exit frame represented by a stack frames
 * invokeSuspend():-1
 * resumeWith()
 *
 */
@ApiStatus.Internal
class CoroutinePreflightFrame(
    val coroutineStacksInfoData: CoroutineStacksInfoData,
    frame: StackFrameProxyImpl,
    val threadPreCoroutineFrames: List<StackFrameProxyImpl>,
    firstFrameVariables: List<JavaValue>
) : CoroutineStackFrame(frame, null, firstFrameVariables) {

    override fun isInLibraryContent() = false

    override fun isSynthetic() = false

}

class CreationCoroutineStackFrame(
    frame: StackFrameProxyImpl,
    sourcePosition: XSourcePosition?,
    private var withSeparator: Boolean,
    location: Location? = frame.safeLocation()
) : CoroutineStackFrame(frame, sourcePosition, emptyList(), false, location), XDebuggerFramesList.ItemWithSeparatorAbove {

    override fun getCaptionAboveOf() =
        KotlinDebuggerCoroutinesBundle.message("coroutine.dump.creation.trace")

    override fun hasSeparatorAbove() = withSeparator
    override fun setWithSeparator(withSeparator: Boolean) {
        this.withSeparator = withSeparator
    }
}

open class CoroutineStackFrame(
    frame: StackFrameProxyImpl,
    private val position: XSourcePosition?,
    private val spilledVariables: List<JavaValue> = emptyList(),
    private val includeFrameVariables: Boolean = true,
    location: Location? = frame.safeLocation(),
) : KotlinStackFrame(
    safeCoroutineStackFrameProxy(location, spilledVariables, frame),
    if (spilledVariables.isEmpty() || includeFrameVariables) {
        InlineStackTraceCalculator.calculateVisibleVariables(frame)
    } else {
        listOf()
    }
) {

    init {
        descriptor.updateRepresentation(null, DescriptorLabelListener.DUMMY_LISTENER)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true

        if (!super.equals(other)) {
            return false
        }

        val frame = other as? JavaStackFrame ?: return false

        return descriptor.frameProxy == frame.descriptor.frameProxy
    }

    override fun hashCode(): Int {
        return descriptor.frameProxy.hashCode()
    }

    override fun buildVariablesThreadAction(debuggerContext: DebuggerContextImpl, children: XValueChildrenList, node: XCompositeNode) {
        if (includeFrameVariables || spilledVariables.isEmpty()) {
            super.buildVariablesThreadAction(debuggerContext, children, node)
            val debugProcess = debuggerContext.debugProcess ?: return
            addOptimisedVariables(debugProcess, children)
        } else {
            // ignore original frame variables
            for (variable in spilledVariables) {
                children.add(variable)
            }
        }
    }

    private fun addOptimisedVariables(debugProcess: DebugProcessImpl, children: XValueChildrenList) {
        val visibleVariableNames by lazy { children.getUniqueNames() }

        for (variable in spilledVariables) {
            val name = variable.name
            if (name !in visibleVariableNames) {
                children.add(variable)
                visibleVariableNames.add(name)
            }
        }

        val declaredVariableNames = findVisibleVariableNames(debugProcess)
        for (name in declaredVariableNames) {
            if (name !in visibleVariableNames) {
                children.add(createOptimisedVariableMessageNode(name))
            }
        }
    }

    private fun createOptimisedVariableMessageNode(name: String) =
        createMessageNode(
            KotlinDebuggerCoroutinesBundle.message("optimised.variable.message", "\'$name\'"),
            AllIcons.General.Information
        )

    private fun XValueChildrenList.getUniqueNames(): MutableSet<String> {
        val names = mutableSetOf<String>()
        for (i in 0 until size()) {
            names.add(getName(i))
        }
        return names
    }

    private fun findVisibleVariableNames(debugProcess: DebugProcessImpl): List<String> {
        val location = stackFrameProxy.safeLocation() ?: return emptyList()
        return ReadAction.nonBlocking<List<String>> {
            KotlinVariableNameFinder(debugProcess)
                .findVisibleVariableNames(location)
        }.executeSynchronously()
    }

    override fun getSourcePosition() =
        position ?: super.getSourcePosition()
}
