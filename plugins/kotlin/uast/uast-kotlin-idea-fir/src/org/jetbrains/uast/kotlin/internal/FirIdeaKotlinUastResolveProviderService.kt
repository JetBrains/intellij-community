// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.uast.kotlin.internal

import org.jetbrains.kotlin.idea.base.util.isOutsiderFile
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.uast.kotlin.FirKotlinUastResolveProviderService

private class FirIdeaKotlinUastResolveProviderService : FirKotlinUastResolveProviderService {
    override fun isSupportedFile(file: KtFile): Boolean {
        val virtualFile = file.virtualFile
        return virtualFile == null || !isOutsiderFile(virtualFile)
    }
}
