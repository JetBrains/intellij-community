// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.j2k

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.nj2k.externalCodeProcessing.ExternalUsagesFixer
import org.jetbrains.kotlin.psi.KtFile

@ApiStatus.Internal
interface ExternalCodeProcessing {

    context(_: KaSession)
    fun bindJavaDeclarationsToConvertedKotlinOnes(files: List<KtFile>)

    fun collectUsages(): List<ExternalUsagesFixer.JKMemberInfoWithUsages>
}
