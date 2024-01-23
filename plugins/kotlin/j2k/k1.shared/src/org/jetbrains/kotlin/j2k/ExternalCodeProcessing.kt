// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.j2k

import com.intellij.openapi.progress.ProgressIndicator
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.psi.KtFile

@ApiStatus.Internal
interface ExternalCodeProcessing {
    fun prepareWriteOperation(progress: ProgressIndicator?): (List<KtFile>) -> Unit
}