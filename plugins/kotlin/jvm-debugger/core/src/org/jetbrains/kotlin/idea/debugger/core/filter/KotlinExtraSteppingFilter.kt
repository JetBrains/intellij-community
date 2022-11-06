// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.debugger.core.filter

import com.intellij.debugger.engine.ExtraSteppingFilter
import com.intellij.debugger.engine.SuspendContext
import com.intellij.debugger.settings.DebuggerSettings
import com.sun.jdi.Location
import com.sun.jdi.request.StepRequest
import org.jetbrains.kotlin.idea.debugger.KotlinPositionManager
import org.jetbrains.kotlin.idea.debugger.base.util.safeAllLineLocations
import org.jetbrains.kotlin.idea.debugger.base.util.safeGetSourcePosition
import org.jetbrains.kotlin.idea.debugger.base.util.safeLocation
import org.jetbrains.kotlin.idea.debugger.base.util.safeMethod
import org.jetbrains.kotlin.idea.debugger.core.*
import com.intellij.openapi.application.runReadAction

class KotlinExtraSteppingFilter : ExtraSteppingFilter {
    override fun isApplicable(context: SuspendContext?): Boolean {
        val debugProcess = context?.debugProcess ?: return false
        val location = context.frameProxy?.safeLocation() ?: return false
        val defaultStratum = location.declaringType()?.defaultStratum() ?: return false

        if (defaultStratum != "Kotlin") {
            return false
        }

        return runReadAction {
            val positionManager = KotlinPositionManager(debugProcess)
            val sourcePosition = positionManager.safeGetSourcePosition(location) ?: return@runReadAction false

            if (isInSuspendMethod(location) && isOnSuspendReturnOrReenter(location) && !isOneLineMethod(location)) {
                return@runReadAction true
            }

            val settings = DebuggerSettings.getInstance()
            if (settings.TRACING_FILTERS_ENABLED) {
                val classNameProvider = ClassNameProvider(
                    debugProcess.project,
                    debugProcess.searchScope,
                    ClassNameProvider.Configuration.DEFAULT.copy(findInlineUseSites = false)
                )

                val classNames = classNameProvider
                    .getCandidates(sourcePosition)
                    .map { it.replace('/', '.') }

                for (className in classNames) {
                    for (filter in settings.steppingFilters) {
                        if (filter.isEnabled && filter.matches(className)) {
                            return@runReadAction true
                        }
                    }
                }
            }

            return@runReadAction false
        }
    }

    override fun getStepRequestDepth(context: SuspendContext?): Int {
        return StepRequest.STEP_INTO
    }
}

private fun isOneLineMethod(location: Location): Boolean {
    val method = location.safeMethod() ?: return false
    val allLineLocations = method.safeAllLineLocations()
    if (allLineLocations.isEmpty()) return false
    if (allLineLocations.size == 1) return true

    val inlineFunctionBorders = method.getInlineFunctionAndArgumentVariablesToBordersMap().values
    return allLineLocations
        .mapNotNull { loc ->
            if (!isKotlinFakeLineNumber(loc) &&
                !inlineFunctionBorders.any { loc in it })
                loc.lineNumber()
            else
                null
        }
        .toHashSet()
        .size == 1
}
