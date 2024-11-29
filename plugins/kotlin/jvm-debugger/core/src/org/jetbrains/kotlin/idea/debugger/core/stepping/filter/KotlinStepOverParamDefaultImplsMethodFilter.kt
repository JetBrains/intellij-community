// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.debugger.core.stepping.filter

import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.MethodFilter
import com.intellij.util.Range
import com.sun.jdi.Location
import com.sun.jdi.Method
import org.jetbrains.kotlin.fileClasses.internalNameWithoutInnerClasses
import org.jetbrains.kotlin.idea.debugger.base.util.KotlinDebuggerConstants
import org.jetbrains.kotlin.idea.debugger.base.util.safeMethod
import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.org.objectweb.asm.Type
import org.jetbrains.org.objectweb.asm.Type.getMethodType

class KotlinStepOverParamDefaultImplsMethodFilter(
    private val name: String,
    private val defaultSignature: String,
    private val containingTypeName: String,
    private val possiblyConvertedToStatic: Boolean,
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
            val possiblyConvertedToStatic = isSyntheticDefaultMethodPossiblyConvertedToStatic(location)
            return KotlinStepOverParamDefaultImplsMethodFilter(
                originalName, signature, containingTypeName,
                possiblyConvertedToStatic, expressionLines
            )
        }
    }

    override fun locationMatches(process: DebugProcessImpl?, location: Location?): Boolean {
        val method = location?.safeMethod() ?: return true
        val containingTypeName = location.declaringType().name()

        return method.name() == name
                && containingTypeName == this.containingTypeName
                && matchesDefaultMethod(method)
    }

    private fun matchesDefaultMethod(method: Method): Boolean =
        matchesDefaultMethodSignature(
            getMethodType(defaultSignature),
            getMethodType(method.signature()),
            possiblyConvertedToStatic,
            isConstructor = name == "<init>",
        )

    override fun getCallingExpressionLines(): Range<Int> = expressionLines
}

internal fun matchesDefaultMethodSignature(defaultType: Type, actualType: Type, possiblyConvertedToStatic: Boolean, isConstructor: Boolean): Boolean {
    val defaultArgTypes = defaultType.argumentTypes
    val actualArgTypes = actualType.argumentTypes

    return actualArgTypes.matchesDefaultSignature(defaultArgTypes, isConstructor)
            || possiblyConvertedToStatic && actualArgTypes.matchesDefaultSignature(defaultArgTypes.drop(1).toTypedArray(), isConstructor)
}

/**
 * Default synthetic method may be converted to a static method with this parameter passes as the first argument.
 * It happens, for example, when a method has generic type parameters, see IDEA-356332.
 */
internal fun isSyntheticDefaultMethodPossiblyConvertedToStatic(location: Location): Boolean {
    val method = location.safeMethod() ?: return false
    return isSyntheticDefaultMethodPossiblyConvertedToStatic(method.isStatic, method.signature(), location.declaringType().signature())
}

internal fun isSyntheticDefaultMethodPossiblyConvertedToStatic(isStatic: Boolean, signature: String, typeSignature: String): Boolean =
    isStatic && signature.startsWith("($typeSignature")

// The default method should have more parameters than the implementation
// because of flags
private fun Array<Type>.matchesDefaultSignature(defaultArgTypes: Array<Type>, isConstructor: Boolean): Boolean {
    val lastArgumentType = if (isConstructor)
        KotlinDebuggerConstants.DEFAULT_CONSTRUCTOR_MARKER_FQ_NAME.internalNameWithoutInnerClasses else "java/lang/Object"
    return (size < defaultArgTypes.size
            && defaultArgTypes.last().internalName == lastArgumentType
            && zip(defaultArgTypes).all { (actualType, defaultType) -> actualType == defaultType }
            && defaultArgTypes.slice(size until defaultArgTypes.size - 1).all { it.sort == Type.INT })
}
