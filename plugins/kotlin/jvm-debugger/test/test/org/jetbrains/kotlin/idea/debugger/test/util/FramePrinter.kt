// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.debugger.test.util

import com.intellij.debugger.SourcePosition
import com.intellij.debugger.engine.DebuggerUtils
import com.intellij.debugger.engine.JavaStackFrame
import com.intellij.debugger.engine.SourcePositionProvider
import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.engine.events.SuspendContextCommandImpl
import com.intellij.debugger.impl.DebuggerUtilsEx
import com.intellij.debugger.ui.impl.watch.*
import com.intellij.debugger.ui.tree.*
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.roots.JdkOrderEntry
import com.intellij.openapi.roots.libraries.LibraryUtil
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.concurrency.Semaphore
import com.intellij.xdebugger.XDebuggerTestUtil
import com.intellij.xdebugger.XTestValueNode
import com.intellij.xdebugger.frame.*
import com.intellij.xdebugger.impl.ui.XDebuggerUIConstants
import com.sun.jdi.ArrayType
import org.jetbrains.kotlin.idea.debugger.core.render.GetterDescriptor
import org.jetbrains.kotlin.idea.debugger.core.invokeInManagerThread
import org.jetbrains.kotlin.idea.debugger.core.stackFrame.DelegateDescriptor
import org.jetbrains.kotlin.idea.debugger.coroutine.data.ContinuationVariableValueDescriptorImpl
import org.jetbrains.kotlin.idea.debugger.test.KOTLIN_LIBRARY_NAME
import org.jetbrains.kotlin.psi.KtFile
import java.util.concurrent.TimeUnit

class FramePrinter(private val suspendContext: SuspendContextImpl) {
    fun print(frame: XStackFrame): String {
        return buildString { appendRecursively(frame, 0) }
    }

    fun printTopVariables(frame: XStackFrame): String {
        return buildString {
            append(frame, 0)
            for (child in frame) {
                append(child, 1)
            }
        }
    }

    private fun Appendable.appendRecursively(container: XValueContainer, indent: Int = 0) {
        append(container, indent)
        for (child in container) {
            appendRecursively(child, indent + 1)
        }
    }

    private fun Appendable.append(container: XValueContainer, indent: Int = 0) {
        appendIndent(indent)

        append(container.javaClass.simpleName)

        val info = computeInfo(container)

        info.kind?.let { append("[$it]") }
        info.name?.let { append(" $it") }
        info.type?.let { append(": $it") }
        info.value?.let { append(" = $it") }
        info.sourcePosition?.let { append(" (" + it.render() + ")") }

        appendLine()
    }

    private class ValueInfo(
        val name: String?,
        val kind: String?,
        val type: String?,
        val value: String?,
        val sourcePosition: SourcePosition?
    )

    private fun computeInfo(container: XValueContainer): ValueInfo {
        val name = container.getName()
        when (container) {
            is XValue -> {
                val node = XTestValueNode()
                container.computePresentation(node, XValuePlace.TREE)
                node.waitFor(XDebuggerTestUtil.TIMEOUT_MS.toLong())

                val descriptor = if (container is NodeDescriptorProvider) container.descriptor else null
                val kind = getLabel(if (descriptor is DelegateDescriptor) descriptor.delegate else descriptor)
                val type = (descriptor as? ValueDescriptorImpl)?.declaredType ?: node.myType?.takeIf { it.isNotEmpty() }
                val value = (computeValue(descriptor) ?: node.myValue).takeIf { it.isNotEmpty() }
                val sourcePosition = computeSourcePosition(descriptor)
                return ValueInfo(name, kind, type, value, sourcePosition)
            }
            is XStackFrame -> {
                val sourcePosition = DebuggerUtilsEx.toSourcePosition(container.sourcePosition, suspendContext.debugProcess.project)
                return ValueInfo(name, kind = null, type = null, value = null, sourcePosition)
            }
            else -> {
                return ValueInfo(name, kind = null, type = null, value = null, sourcePosition = null)
            }
        }
    }

    private fun XValueContainer.getName() =
        when (this) {
            is XNamedValue -> name.takeIf { it.isNotEmpty() }
            is JavaStackFrame -> descriptor.name
            else -> null
        }

    private fun computeValue(descriptor: NodeDescriptorImpl?): String? {
        val valueDescriptor = descriptor as? ValueDescriptorImpl ?: return null
        if (valueDescriptor is GetterDescriptor) {
            return null
        }

        if (valueDescriptor.isMapEntryDescriptor) {
            return MAP_ENTRY_TEST_LABEL
        } else if (valueDescriptor.isArrayDescriptor) {
            return ARRAY_TEST_LABEL
        }

        val semaphore = Semaphore()
        semaphore.down()

        var result: String? = null

        fun updateResult(value: String) {
            result = patchHashCode(value)
            semaphore.up()
        }

        suspendContext.managerThread.schedule(object : SuspendContextCommandImpl(suspendContext) {
            override fun contextAction(suspendContext: SuspendContextImpl) {
                val evaluationContext = EvaluationContextImpl(suspendContext, suspendContext.frameProxy)
                valueDescriptor.setContext(evaluationContext)
                val renderer = valueDescriptor.getRenderer(suspendContext.debugProcess)
                    ?.get(XDebuggerTestUtil.TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)

                if (renderer == null) {
                    semaphore.up()
                    return
                }

                val immediateValue = renderer.calcLabel(descriptor, evaluationContext) { updateResult(descriptor.valueText) }

                if (immediateValue != XDebuggerUIConstants.getCollectingDataMessage()) {
                    updateResult(immediateValue)
                }
            }

            override fun commandCancelled() {
                semaphore.up()
            }
        })

        semaphore.waitFor(XDebuggerTestUtil.TIMEOUT_MS.toLong())
        return result
    }

    private fun computeSourcePosition(descriptor: NodeDescriptorImpl?): SourcePosition? {
        if (descriptor == null) {
            return null
        }

        val debugProcess = suspendContext.debugProcess
        return debugProcess.invokeInManagerThread { debuggerContext ->
            runReadAction {
                SourcePositionProvider.getSourcePosition(descriptor, debugProcess.project, debuggerContext)
            }
        }
    }

    private fun getLabel(descriptor: NodeDescriptorImpl?): String? {
        return when (descriptor) {
            is GetterDescriptor -> "getter"
            is StackFrameDescriptor -> "frame"
            is WatchItemDescriptor -> "watch"
            is LocalVariableDescriptor -> "local"
            is StaticDescriptor -> "static"
            is ThisDescriptorImpl -> "this"
            is FieldDescriptor -> "field"
            is ArrayElementDescriptor -> "element"
            is ContinuationVariableValueDescriptorImpl -> "continuation"
            else -> null
        }
    }

    private fun Appendable.appendIndent(indent: Int) {
        repeat(indent) { append("    ") }
    }
}

fun SourcePosition.render(): String {
    val virtualFile = file.originalFile.virtualFile ?: file.viewProvider.virtualFile

    val libraryEntry = runReadAction {  LibraryUtil.findLibraryEntry(virtualFile, file.project) }
    if (libraryEntry != null && (libraryEntry is JdkOrderEntry || libraryEntry.presentableName == KOTLIN_LIBRARY_NAME)) {
        val suffix = if (isInCompiledFile()) "COMPILED" else "EXT"
        return FileUtil.getNameWithoutExtension(virtualFile.name) + ".!$suffix!"
    }

    return virtualFile.name + ":" + (line + 1)
}

private fun SourcePosition.isInCompiledFile(): Boolean {
    val ktFile = file as? KtFile ?: return false
    return ktFile.isCompiled
}

private val HASH_CODE_REGEX = "^(.*@)[0-9a-f]+$".toRegex()

private fun patchHashCode(value: String): String {
    val match = HASH_CODE_REGEX.matchEntire(value) ?: return value
    return match.groupValues[1] + "hashCode"
}

/**
 * We have a platform renderer for `Map.Entry` class which renders its label as "key -> value".
 *
 * It works fine in the real IDEA instance; however, it needs other renderers to correctly render key and value,
 * and it fetches them asynchronously. Because of that it is unable to correctly create a label
 * for `Map.Entry` object from the first try; it creates some dummy label (usually " -> "),
 * and then (when the renderers are fetched) it updates the label.
 *
 * It makes the tests flaky, because we can observe the either dummy value, the final one, or something in between.
 *
 * To avoid that, we do not render `Map.Entry` objects at all, and use this placeholder to get stable results.
 *
 * (See com.intellij.debugger.settings.NodeRendererSettings.MapEntryLabelRenderer.calcLabel method for
 * the implementation of labels calculation)
 */
private const val MAP_ENTRY_TEST_LABEL = "... -> ..."

private val ValueDescriptorImpl.isMapEntryDescriptor
    get() = DebuggerUtils.instanceOf(type, "java.util.Map.Entry")

/**
 * Rendering of array types is performed by com.intellij.debugger.ui.tree.render.ArrayRenderer.
 * To render an array correctly, it has to fetch all of its values. After that the renderer
 * asynchronously updates the array representation, which results in flaky tests.
 */
private const val ARRAY_TEST_LABEL = "[...]"

private val ValueDescriptorImpl.isArrayDescriptor
    get() = type is ArrayType
