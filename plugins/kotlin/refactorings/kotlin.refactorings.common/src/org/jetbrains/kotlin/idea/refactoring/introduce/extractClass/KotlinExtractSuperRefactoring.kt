// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.introduce.extractClass

import com.intellij.openapi.components.service
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface KotlinExtractSuperRefactoring {
    fun performRefactoring(extractInfo: ExtractSuperInfo)

    companion object {
        @JvmStatic
        fun getInstance(): KotlinExtractSuperRefactoring = service<KotlinExtractSuperRefactoring>()
    }
}
