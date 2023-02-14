// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.debugger.coroutine.view

import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.debugger.engine.JavaExecutionStack
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.ui.DoubleClickListener
import com.intellij.xdebugger.frame.XExecutionStack
import com.intellij.xdebugger.frame.XStackFrame
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl
import org.jetbrains.kotlin.analysis.decompiler.stub.file.ClsClassFinder.isKotlinInternalCompiledFile
import org.jetbrains.kotlin.idea.debugger.coroutine.data.RunningCoroutineStackFrameItem
import org.jetbrains.kotlin.idea.debugger.core.invokeInManagerThread
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent

class CoroutineSelectedNodeListener(
    private val debugProcess: DebugProcessImpl,
    private val tree: XDebuggerTree
) {
    fun install() {
        object : DoubleClickListener() {
            override fun onDoubleClick(e: MouseEvent): Boolean =
                processSelectedNode()
        }.installOn(tree)

        tree.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                val key = e.keyCode
                if (key == KeyEvent.VK_ENTER || key == KeyEvent.VK_SPACE || key == KeyEvent.VK_RIGHT) {
                    processSelectedNode()
                }
            }
        })
    }

    private fun processSelectedNode(): Boolean {
        val selectedNodes = tree.getSelectedNodes(XValueNodeImpl::class.java, null)
        val valueContainer = selectedNodes.getSingleCoroutineFrameValueContainer() ?: return false
        val frameItem = valueContainer.frameItem
        val frame = frameItem.createFrame(debugProcess) ?: return false
        val executionStack =
            if (frameItem is RunningCoroutineStackFrameItem)
                createExecutionStack(frameItem.frame.threadProxy(), debugProcess)
            else
                debugProcess.suspendContext.thread?.let {
                    createExecutionStack(it, debugProcess)
                }

        if (executionStack != null) {
            setCurrentStackFrame(executionStack, frame)
        }
        return true
    }

    private fun setCurrentStackFrame(executionStack: XExecutionStack, stackFrame: XStackFrame) {
        val fileToNavigate = stackFrame.sourcePosition?.file ?: return
        val session = debugProcess.session.xDebugSession ?: return
        if (!isKotlinInternalCompiledFile(fileToNavigate)) {
            ApplicationManager.getApplication().invokeLater({
                session.setCurrentStackFrame(executionStack, stackFrame, false)
            }, ModalityState.stateForComponent(tree))
        }
    }
}

private fun createExecutionStack(threadReference: ThreadReferenceProxyImpl, debugProcess: DebugProcessImpl): JavaExecutionStack? =
    debugProcess.invokeInManagerThread {
        val executionStack = JavaExecutionStack(
            threadReference,
            debugProcess,
            debugProcess.suspendContext.thread == threadReference
        )
        executionStack.initTopFrame()
        executionStack
    }

private fun Array<XValueNodeImpl>.getSingleCoroutineFrameValueContainer(): CoroutineView.CoroutineFrameValue? =
    singleOrNull()?.valueContainer as? CoroutineView.CoroutineFrameValue

private val DebugProcessImpl.suspendContext: SuspendContextImpl
    get() = suspendManager.pausedContext
