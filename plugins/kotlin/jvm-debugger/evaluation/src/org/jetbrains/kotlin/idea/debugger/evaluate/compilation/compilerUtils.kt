/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.kotlin.idea.debugger.evaluate.compilation

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.components.KaCompiledFile
import org.jetbrains.kotlin.idea.debugger.base.util.internalNameToFqn
import org.jetbrains.kotlin.idea.debugger.evaluate.classLoading.ClassToLoad
import org.jetbrains.kotlin.idea.debugger.evaluate.classLoading.isEvaluationEntryPoint
import org.jetbrains.org.objectweb.asm.*
import org.jetbrains.org.objectweb.asm.ClassReader.SKIP_CODE


fun getMethodSignature(
    fragmentClass: ClassToLoad,
): CompiledCodeFragmentData.MethodSignature {
    val parameters: MutableList<Type> = mutableListOf()
    var returnType: Type? = null

    ClassReader(fragmentClass.bytes).accept(object : ClassVisitor(Opcodes.ASM7) {
        override fun visitMethod(
            access: Int,
            name: String?,
            descriptor: String?,
            signature: String?,
            exceptions: Array<out String>?
        ): MethodVisitor? {
            if (name != null && isEvaluationEntryPoint(name)) {
                Type.getArgumentTypes(descriptor).forEach { parameters.add(it) }
                returnType = Type.getReturnType(descriptor)
            }
            return null
        }
    }, SKIP_CODE)

    return CompiledCodeFragmentData.MethodSignature(parameters, returnType!!)
}

@KaExperimentalApi
internal val KaCompiledFile.internalClassName: String
    get() = computeInternalClassName(path)

@ApiStatus.Internal
fun computeInternalClassName(path: String): String {
    require(path.endsWith(".class", ignoreCase = true))
    return path.dropLast(".class".length).internalNameToFqn()
}
