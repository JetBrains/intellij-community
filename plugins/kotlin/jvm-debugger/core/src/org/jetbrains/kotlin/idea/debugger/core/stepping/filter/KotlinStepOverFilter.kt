// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.debugger.core.stepping.filter

import com.intellij.debugger.engine.DebugProcess.JAVA_STRATUM
import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.util.Range
import com.sun.jdi.LocalVariable
import com.sun.jdi.Location
import com.sun.jdi.StackFrame
import org.jetbrains.kotlin.idea.debugger.base.util.safeLineNumber
import org.jetbrains.kotlin.idea.debugger.base.util.safeMethod
import org.jetbrains.kotlin.idea.debugger.base.util.safeVariables
import org.jetbrains.kotlin.idea.debugger.core.isKotlinFakeLineNumber
import org.jetbrains.kotlin.idea.debugger.core.stepping.KotlinMethodFilter
import org.jetbrains.kotlin.load.java.JvmAbi

data class StepOverCallerInfo(val declaringType: String, val methodName: String?, val methodSignature: String?) {
    companion object {
        fun from(location: Location): StepOverCallerInfo {
            val method = location.safeMethod()
            val declaringType = location.declaringType().name()
            val methodName = method?.name()
            val methodSignature = method?.signature()
            return StepOverCallerInfo(declaringType, methodName, methodSignature)
        }
    }
}

data class LocationToken(val lineNumber: Int, val inlineVariables: List<LocalVariable>) {
    companion object {
        fun from(stackFrame: StackFrame): LocationToken {
            val location = stackFrame.location()
            val lineNumber = location.safeLineNumber(JAVA_STRATUM)
            val methodVariables = ArrayList<LocalVariable>(0)

            for (variable in location.safeMethod()?.safeVariables() ?: emptyList()) {
                val name = variable.name()
                if (variable.isVisible(stackFrame) && JvmAbi.isFakeLocalVariableForInline(name)) {
                    methodVariables += variable
                }
            }

            return LocationToken(lineNumber, methodVariables)
        }
    }
}

abstract class KotlinStepOverFilter(locationStepOverStartedFrom: Location) : KotlinMethodFilter {
    private val callerInfo = StepOverCallerInfo.from(locationStepOverStartedFrom)

    override fun locationMatches(context: SuspendContextImpl, location: Location): Boolean {
        // Never stop on compiler generated fake line numbers.
        if (isKotlinFakeLineNumber(location)) return false

        val stackFrame = context.frameProxy?.stackFrame ?: return true
        val callerInfo = StepOverCallerInfo.from(location)
        if (callerInfo.methodName != null && callerInfo.methodSignature != null && this.callerInfo == callerInfo) {
            return isAcceptable(location, LocationToken.from(stackFrame), stackFrame)
        }
        return true
    }

    abstract fun isAcceptable(location: Location, locationToken: LocationToken, stackFrame: StackFrame): Boolean

    override fun locationMatches(process: DebugProcessImpl, location: Location): Boolean {
        throw IllegalStateException("Should not be called from Kotlin hint")
    }

    override fun getCallingExpressionLines(): Range<Int>? = null
}
