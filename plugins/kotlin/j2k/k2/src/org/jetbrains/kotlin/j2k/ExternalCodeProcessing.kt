// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.j2k

import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.j2k.externalCodeProcessing.ExternalUsagesFixer
import org.jetbrains.kotlin.psi.KtFile

@ApiStatus.Internal
interface ExternalCodeProcessing {

    context(_: KaSession)
    fun bindJavaDeclarationsToConvertedKotlinOnes(files: List<KtFile>)

    /**
     * Searches the usages of the converted members one by one, each member in its own smart read action.
     * [onProgress] gets the done fraction of the whole search and the member at [index] of [total] that the search processes now.
     */
    suspend fun collectUsages(
        project: Project,
        onProgress: ((fraction: Double, member: String, index: Int, total: Int) -> Unit)? = null,
    ): List<ExternalUsagesFixer.JKMemberInfoWithUsages>
}
