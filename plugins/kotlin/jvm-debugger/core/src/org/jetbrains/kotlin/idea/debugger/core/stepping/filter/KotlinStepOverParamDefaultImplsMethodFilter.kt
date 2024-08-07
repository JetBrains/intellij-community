// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.debugger.core.stepping.filter

import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.MethodFilter
import com.intellij.util.Range
import com.sun.jdi.Location
import org.jetbrains.kotlin.idea.debugger.base.util.safeMethod
import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.org.objectweb.asm.Type

class KotlinStepOverParamDefaultImplsMethodFilter(
    private val name: String,
    private val defaultSignature: String,
    private val containingTypeName: String,
    private val expressionLines: Range<Int>
) : MethodFilter {
    companion object {
        fun create(location: Location, expressionLines: Range<Int>): KotlinStepOverParamDefaultImplsMethodFilter? {
            if (location.lineNumber() < 0) {
                return null
            }

            val method = location.safeMethod() ?: return null
            val name = method.name()
            val originalName = if (name.endsWith(JvmAbi.DEFAULT_PARAMS_IMPL_SUFFIX)) {
                name.removeSuffix(JvmAbi.DEFAULT_PARAMS_IMPL_SUFFIX)
            } else if (name == "<init>") {
                name
            } else {
                error("Unexpected default impls method: $name")
            }
            val signature = method.signature()
            val containingTypeName = location.declaringType().name()
            return KotlinStepOverParamDefaultImplsMethodFilter(originalName, signature, containingTypeName, expressionLines)
        }
    }

    override fun locationMatches(process: DebugProcessImpl?, location: Location?): Boolean {
        val method = location?.safeMethod() ?: return true
        val containingTypeName = location.declaringType().name()

        return method.name() == name
                && containingTypeName == this.containingTypeName
                && signatureMatches(defaultSignature, method.signature())
    }

    private fun signatureMatches(default: String, actual: String): Boolean {
        val defaultType = Type.getMethodType(default)
        val actualType = Type.getMethodType(actual)

        val defaultArgTypes = defaultType.argumentTypes
        val actualArgTypes = actualType.argumentTypes

        if (defaultArgTypes.size <= actualArgTypes.size) {
            // Default method should have more parameters than the implementation
            // because of flags
            return false
        }

        for ((index, type) in actualArgTypes.withIndex()) {
            if (defaultArgTypes[index] != type) {
                return false
            }
        }

        return true
    }

    override fun getCallingExpressionLines(): Range<Int> = expressionLines
}
