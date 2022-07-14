// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.debugger.filter

import com.intellij.debugger.engine.ExtraSteppingFilter
import com.intellij.debugger.engine.SuspendContext
import com.intellij.debugger.settings.DebuggerSettings
import com.sun.jdi.request.StepRequest
import org.jetbrains.kotlin.idea.debugger.*
import org.jetbrains.kotlin.idea.util.application.runReadAction

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
                val classNames = ClassNameProvider(debugProcess.project, debugProcess.searchScope, findInlineUseSites = false)
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

