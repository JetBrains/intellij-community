// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.evaluate.compilation

import org.jetbrains.kotlin.idea.debugger.evaluate.classLoading.ClassToLoad
import org.jetbrains.org.objectweb.asm.Type

data class CompiledCodeFragmentData(
    val classes: List<ClassToLoad>,
    val parameters: List<CodeFragmentParameter.Dumb>,
    val crossingBounds: Set<CodeFragmentParameter.Dumb>,
    val mainMethodSignature: MethodSignature
) {
    data class MethodSignature(val parameterTypes: List<Type>, val returnType: Type)
}

val CompiledCodeFragmentData.mainClass: ClassToLoad
    get() = classes.first { it.isMainClass }