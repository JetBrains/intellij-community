// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.j2k

import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile

internal class K2J2KPostProcessor : PostProcessor {
    override val phasesCount = 0

    override fun insertImport(file: KtFile, fqName: FqName) {
        TODO("Not supported in K2 J2K yet")
    }

    override fun doAdditionalProcessing(
        target: JKPostProcessingTarget,
        converterContext: ConverterContext?,
        onPhaseChanged: ((Int, String) -> Unit)?
    ) {
        // Do nothing
    }
}