// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.j2k

import com.intellij.psi.PsiJavaFile
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
data class ConversionResult(
    val kotlinCodeByJavaFile: Map<PsiJavaFile, String>,
    val externalCodeProcessing: ExternalCodeProcessing?,
    val javaLines: Int = 0,
    val kotlinLines: Int = 0,
)
