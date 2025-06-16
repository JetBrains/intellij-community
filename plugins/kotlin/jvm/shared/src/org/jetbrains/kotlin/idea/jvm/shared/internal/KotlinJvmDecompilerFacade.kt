// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.jvm.shared.internal

import com.intellij.openapi.components.serviceOrNull
import org.jetbrains.kotlin.psi.KtFile

class DecompileFailedException(message: String, cause: Throwable) : RuntimeException(message, cause)

interface KotlinJvmDecompilerFacade {
    fun showDecompiledCode(sourceFile: KtFile)

    companion object {
        fun getInstance(): KotlinJvmDecompilerFacade? = serviceOrNull()
    }
}
