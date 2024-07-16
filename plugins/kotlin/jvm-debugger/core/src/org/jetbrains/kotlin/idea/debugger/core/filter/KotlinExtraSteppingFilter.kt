// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.debugger.core.filter

import com.intellij.debugger.engine.ExtraSteppingFilter
import com.intellij.debugger.engine.SuspendContext
import com.intellij.debugger.settings.DebuggerSettings
import com.intellij.openapi.application.runReadAction
import com.sun.jdi.request.StepRequest
import org.jetbrains.kotlin.idea.debugger.KotlinPositionManager
import org.jetbrains.kotlin.idea.debugger.base.util.internalNameToFqn
import org.jetbrains.kotlin.idea.debugger.base.util.safeGetSourcePosition
import org.jetbrains.kotlin.idea.debugger.base.util.safeLocation
import org.jetbrains.kotlin.idea.debugger.core.ClassNameProvider
import org.jetbrains.kotlin.idea.debugger.core.isInKotlinSources

class KotlinExtraSteppingFilter : ExtraSteppingFilter {
    override fun isApplicable(context: SuspendContext?): Boolean {
        val debugProcess = context?.debugProcess ?: return false
        val location = context.frameProxy?.safeLocation() ?: return false

        if (!location.isInKotlinSources()) {
            return false
        }

        return runReadAction {
            val positionManager = KotlinPositionManager(debugProcess)
            val sourcePosition = positionManager.safeGetSourcePosition(location) ?: return@runReadAction false

            val settings = DebuggerSettings.getInstance()
            if (settings.TRACING_FILTERS_ENABLED) {
                val classNames = ClassNameProvider()
                    .getCandidates(sourcePosition)
                    .map { it.internalNameToFqn() }

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
