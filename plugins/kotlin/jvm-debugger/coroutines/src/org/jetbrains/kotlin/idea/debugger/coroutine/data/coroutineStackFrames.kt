package org.jetbrains.kotlin.idea.debugger.coroutine.data

import com.intellij.debugger.engine.JavaStackFrame
import com.intellij.debugger.engine.JavaValue
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.jdi.StackFrameProxyImpl
import com.intellij.debugger.ui.tree.render.DescriptorLabelListener
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.frame.XCompositeNode
import com.intellij.xdebugger.frame.XValueChildrenList
import com.intellij.xdebugger.impl.frame.XDebuggerFramesList
import com.sun.jdi.Location
import org.jetbrains.kotlin.idea.debugger.coroutine.KotlinDebuggerCoroutinesBundle
import org.jetbrains.kotlin.idea.debugger.coroutine.proxy.CoroutineStackFrameProxyImpl
import org.jetbrains.kotlin.idea.debugger.safeLocation
import org.jetbrains.kotlin.idea.debugger.stackFrame.KotlinStackFrame

/**
 * Coroutine exit frame represented by a stack frames
 * invokeSuspend():-1
 * resumeWith()
 *
 */
class CoroutinePreflightFrame(
    val coroutineInfoData: CoroutineInfoData,
    val frame: StackFrameProxyImpl,
    val threadPreCoroutineFrames: List<StackFrameProxyImpl>,
    val mode: SuspendExitMode,
    firstFrameVariables: List<JavaValue> = coroutineInfoData.topFrameVariables()
) : CoroutineStackFrame(frame, null, firstFrameVariables) {

    override fun isInLibraryContent() = false

    override fun isSynthetic() = false

}

class CreationCoroutineStackFrame(
    frame: StackFrameProxyImpl,
    sourcePosition: XSourcePosition?,
    val first: Boolean,
    location: Location? = frame.safeLocation()
) : CoroutineStackFrame(frame, sourcePosition, emptyList(), false, location), XDebuggerFramesList.ItemWithSeparatorAbove {

    override fun getCaptionAboveOf() =
        KotlinDebuggerCoroutinesBundle.message("coroutine.dump.creation.trace")

    override fun hasSeparatorAbove() =
        first
}

open class CoroutineStackFrame(
    frame: StackFrameProxyImpl,
    private val position: XSourcePosition?,
    private val spilledVariables: List<JavaValue>? = null,
    private val includeFrameVariables: Boolean = true,
    location: Location? = frame.safeLocation(),
) : KotlinStackFrame(CoroutineStackFrameProxyImpl(location, spilledVariables ?: emptyList(), frame)) {

    init {
        descriptor.updateRepresentation(null, DescriptorLabelListener.DUMMY_LISTENER)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true

        val frame = other as? JavaStackFrame ?: return false

        return descriptor.frameProxy == frame.descriptor.frameProxy
    }

    override fun hashCode(): Int {
        return descriptor.frameProxy.hashCode()
    }

    override fun computeChildren(node: XCompositeNode) {
        if (includeFrameVariables || spilledVariables == null) {
            super.computeChildren(node)
        } else {
            // ignore original frame variables
            val list = XValueChildrenList()
            spilledVariables.forEach { list.add(it) }
            node.addChildren(list, true)
        }
    }

    override fun superBuildVariables(evaluationContext: EvaluationContextImpl, children: XValueChildrenList) {
        super.superBuildVariables(evaluationContext, children)
        if (spilledVariables != null) {
            children.let {
                val varNames = (0 until children.size()).map { children.getName(it) }.toSet()
                spilledVariables.forEach {
                    if (!varNames.contains(it.name))
                        children.add(it)
                }
            }
        }
    }

    override fun getSourcePosition() =
        position ?: super.getSourcePosition()
}
