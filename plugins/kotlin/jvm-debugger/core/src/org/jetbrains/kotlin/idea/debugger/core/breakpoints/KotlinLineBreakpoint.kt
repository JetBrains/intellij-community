// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.debugger.core.breakpoints

import com.intellij.debugger.engine.DebugProcess
import com.intellij.debugger.impl.DebuggerUtilsEx
import com.intellij.debugger.ui.breakpoints.LineBreakpoint
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.breakpoints.XBreakpoint
import com.intellij.xdebugger.breakpoints.XBreakpointProperties
import com.sun.jdi.ReferenceType
import org.jetbrains.java.debugger.breakpoints.properties.JavaLineBreakpointProperties
import org.jetbrains.kotlin.codegen.inline.KOTLIN_STRATA_NAME
import org.jetbrains.kotlin.idea.debugger.base.util.safeSourceName
import org.jetbrains.kotlin.idea.debugger.getContainingMethod

class KotlinLineBreakpoint(
    project: Project?,
    xBreakpoint: XBreakpoint<out XBreakpointProperties<*>>?
) : LineBreakpoint<JavaLineBreakpointProperties>(project, xBreakpoint, false) {
    override fun processClassPrepare(debugProcess: DebugProcess?, classType: ReferenceType?) {
        val sourcePosition = runReadAction { xBreakpoint?.sourcePosition }

        if (classType != null && sourcePosition != null) {
            if (!canHaveTargetLine(classType, sourcePosition)) {
                return
            }
        }

        super.processClassPrepare(debugProcess, classType)
    }

    /**
     * Returns false if `classType` definitely does not contain a location for a given `sourcePosition`
     * in the view of line number information available to the debugger.
     */
    private fun canHaveTargetLine(classType: ReferenceType, sourcePosition: XSourcePosition): Boolean {
        // If we do not have information about lines for this class at all, it does not contain this location.
        val allLineLocations = DebuggerUtilsEx.allLineLocations(classType) ?: return false

        val fileName = sourcePosition.file.name
        val lineNumber = sourcePosition.line + 1

        for (location in allLineLocations) {
            val kotlinFileName = location.safeSourceName(KOTLIN_STRATA_NAME)
            val kotlinLineNumber = location.lineNumber(KOTLIN_STRATA_NAME)

            if (kotlinFileName != null) {
                if (kotlinFileName == fileName && kotlinLineNumber == lineNumber) {
                    return true
                }
            } else {
                if (location.safeSourceName() == fileName && location.lineNumber() == lineNumber) {
                    return true
                }
            }
        }

        return false
    }

    override fun computeMethodName(): String? =
        runReadAction { sourcePosition?.elementAt?.getContainingMethod()?.name } ?: super.computeMethodName()
}
