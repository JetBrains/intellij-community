// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.debugger.stepping.smartStepInto

import com.intellij.debugger.SourcePosition
import com.intellij.debugger.actions.JvmSmartStepIntoHandler
import com.intellij.debugger.actions.SmartStepTarget
import com.intellij.debugger.engine.MethodFilter
import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.debugger.engine.events.DebuggerContextCommandImpl
import com.intellij.debugger.impl.DebuggerSession
import com.intellij.debugger.impl.PrioritizedTask
import com.intellij.openapi.application.ReadAction
import com.intellij.psi.PsiDocumentManager
import com.intellij.util.Range
import com.intellij.util.containers.OrderedSet
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.compute
import org.jetbrains.kotlin.idea.core.util.CodeInsightUtils.getTopmostElementAtOffset
import org.jetbrains.kotlin.idea.debugger.KotlinDebuggerSettings
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile

class KotlinSmartStepIntoHandler : JvmSmartStepIntoHandler() {
    override fun isAvailable(position: SourcePosition?) = position?.file is KtFile

    override fun findStepIntoTargets(position: SourcePosition?, session: DebuggerSession?) =
        if (KotlinDebuggerSettings.getInstance().alwaysDoSmartStepInto) {
            super.findSmartStepTargetsAsync(position, session)
        } else {
            super.findStepIntoTargets(position, session)
        }

    override fun findSmartStepTargetsAsync(position: SourcePosition, session: DebuggerSession): Promise<List<SmartStepTarget>> {
        val result = AsyncPromise<List<SmartStepTarget>>()
        session.process.managerThread.schedule(
            object : DebuggerContextCommandImpl(session.contextManager.context) {
                override fun threadAction(suspendContext: SuspendContextImpl) =
                    result.compute { findSmartStepTargetsInReadAction(position) }

                override fun commandCancelled() {
                    result.setError("Cancelled")
                }

                override fun getPriority() =
                    PrioritizedTask.Priority.NORMAL
            })
        return result
    }

    override fun findSmartStepTargets(position: SourcePosition): List<SmartStepTarget> =
        findSmartStepTargetsInReadAction(position)

    override fun createMethodFilter(stepTarget: SmartStepTarget?): MethodFilter? {
        return when (stepTarget) {
            is KotlinSmartStepTarget -> stepTarget.createMethodFilter()
            else -> super.createMethodFilter(stepTarget)
        }
    }
}

private fun findSmartStepTargetsInReadAction(position: SourcePosition) =
    ReadAction.nonBlocking<List<SmartStepTarget>> {
        findSmartStepTargets(position)
    }.executeSynchronously()

private fun findSmartStepTargets(position: SourcePosition): List<SmartStepTarget> {
    val element = position.elementAt ?: return emptyList()
    val ktElement = getTopmostElementAtOffset(element, element.textRange.startOffset) as? KtElement
    val elementTextRange = ktElement?.textRange ?: return emptyList()
    val file = position.file
    val document = PsiDocumentManager.getInstance(file.project).getDocument(file) ?: return emptyList()
    val lines = Range(document.getLineNumber(elementTextRange.startOffset), document.getLineNumber(elementTextRange.endOffset))
    val consumer = OrderedSet<SmartStepTarget>()
    val visitor = SmartStepTargetVisitor(ktElement, lines, consumer)
    ktElement.accept(visitor, null)
    return consumer
}
