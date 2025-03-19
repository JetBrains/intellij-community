// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.evaluate

import com.intellij.debugger.DebuggerManagerEx
import com.intellij.debugger.impl.DebuggerContextImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode

@ApiStatus.Internal
object DebugContextProvider {
    private val DEBUG_CONTEXT_FOR_TEST: Key<DebuggerContextImpl> = Key.create("DEBUG_CONTEXT_FOR_TESTS")

    @TestOnly
    fun supplyTestDebugContext(contextElement: PsiElement, context: DebuggerContextImpl) {
        contextElement.putCopyableUserData(DEBUG_CONTEXT_FOR_TEST, context)
    }

    fun getDebuggerContext(project: Project, context: PsiElement?): DebuggerContextImpl? {
        return if (isUnitTestMode()) {
            context?.getCopyableUserData(DEBUG_CONTEXT_FOR_TEST)
        } else {
            DebuggerManagerEx.getInstanceEx(project).context
        }
    }
}