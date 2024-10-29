// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.evaluate.compilation

import org.jetbrains.kotlin.idea.debugger.evaluate.CompilerType
import org.jetbrains.kotlin.idea.debugger.evaluate.classLoading.ClassToLoad

data class CompilationResult(
    val classes: List<ClassToLoad>,
    val parameterInfo: CodeFragmentParameterInfo,
    val localFunctionSuffixes: Map<CodeFragmentParameter.Dumb, String>,
    val mainMethodSignature: CompiledCodeFragmentData.MethodSignature,
    val compilerType: CompilerType
)