// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.evaluate.compilation

import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.impl.DebuggerUtilsImpl
import com.intellij.xdebugger.impl.XDebugSessionImpl
import com.intellij.xdebugger.impl.ui.tree.ValueMarkup
import com.sun.jdi.ObjectReference
import com.sun.jdi.Value
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode

object MarkupUtils {
    fun getMarkupMap(debugProcess: DebugProcessImpl) = doGetMarkupMap(debugProcess) ?: emptyMap()

    private fun doGetMarkupMap(debugProcess: DebugProcessImpl): Map<out Value?, ValueMarkup>? {
        if (isUnitTestMode()) {
            return DebuggerUtilsImpl.getValueMarkers(debugProcess)?.allMarkers as Map<ObjectReference, ValueMarkup>?
        }

        val debugSession = debugProcess.session.xDebugSession as? XDebugSessionImpl

        @Suppress("UNCHECKED_CAST")
        return debugSession?.valueMarkers?.allMarkers?.filterKeys { it is Value? } as Map<out Value?, ValueMarkup>?
    }
}