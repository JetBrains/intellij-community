// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.debugger.core.breakpoints

import com.intellij.debugger.ui.breakpoints.JavaLineBreakpointType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.xdebugger.breakpoints.XBreakpointType
import com.intellij.xdebugger.breakpoints.XLineBreakpointType
import org.jetbrains.debugger.SourceInfo
import org.jetbrains.kotlin.idea.debugger.breakpoints.KotlinBreakpointType
import org.jetbrains.kotlin.idea.debugger.breakpoints.KotlinFieldBreakpointType
import org.jetbrains.kotlin.idea.debugger.breakpoints.KotlinLineBreakpointType
import org.jetbrains.kotlin.psi.KtFile

class BreakpointChecker {
    companion object {
        val BREAKPOINT_TYPES = mapOf(
            KotlinLineBreakpointType::class.java to BreakpointType.Line,
            KotlinFieldBreakpointType::class.java to BreakpointType.Field,
            KotlinFunctionBreakpointType::class.java to BreakpointType.Function,
            JavaLineBreakpointType.LambdaJavaBreakpointVariant::class.java to BreakpointType.Lambda,
            KotlinLineBreakpointType.LineKotlinBreakpointVariant::class.java to BreakpointType.Line,
            KotlinLineBreakpointType.KotlinBreakpointVariant::class.java to BreakpointType.All,
            JavaLineBreakpointType.ConditionalReturnJavaBreakpointVariant::class.java to BreakpointType.Return,
        )
    }

    enum class BreakpointType(val prefix: String) {
        Line("L"),
        Field("F"),
        Function("M"), // method
        Lambda("λ"),
        Return("R"),
        All("*") // line & lambda
    }

    private val breakpointTypes: List<XLineBreakpointType<*>> = run {
        val extensionPoint = ApplicationManager.getApplication().extensionArea
            .getExtensionPoint<XBreakpointType<*, *>>(XBreakpointType.EXTENSION_POINT_NAME.name)

        extensionPoint.extensions
            .filterIsInstance<XLineBreakpointType<*>>()
            .filter { it is KotlinBreakpointType }
    }

    fun check(file: KtFile, line: Int): List<BreakpointType> {
        val actualBreakpointTypes = mutableListOf<BreakpointType>()

        for (breakpointType in breakpointTypes) {
            val sign = BREAKPOINT_TYPES[breakpointType.javaClass] ?: continue
            val isApplicable = breakpointType.canPutAt(file.virtualFile, line, file.project)

            if (breakpointType is KotlinLineBreakpointType) {
                if (isApplicable) {
                    val variants = breakpointType.computeVariants(file.project, SourceInfo(file.virtualFile, line))
                    if (variants.isNotEmpty()) {
                        actualBreakpointTypes += variants.mapNotNull { BREAKPOINT_TYPES[it.javaClass] }
                    } else {
                        actualBreakpointTypes += sign
                    }
                }
            } else if (isApplicable) {
                actualBreakpointTypes += sign
            }
        }

        return actualBreakpointTypes
    }
}