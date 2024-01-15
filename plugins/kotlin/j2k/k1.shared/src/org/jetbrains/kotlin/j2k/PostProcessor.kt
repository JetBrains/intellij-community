// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.j2k

import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile

interface PostProcessor {
    fun insertImport(file: KtFile, fqName: FqName)

    val phasesCount: Int

    fun doAdditionalProcessing(
        target: JKPostProcessingTarget,
        converterContext: ConverterContext?,
        onPhaseChanged: ((Int, String) -> Unit)?
    )
}