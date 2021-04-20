// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin.internal

import com.intellij.psi.PsiElement
import org.jetbrains.uast.kotlin.FirKotlinUastResolveProviderService

class FirCliKotlinUastResolveProviderService : FirKotlinUastResolveProviderService {
    // Currently, UAST CLI is used by Android Lint, i.e., everything is a JVM element.
    override fun isJvmElement(psiElement: PsiElement): Boolean = true
}
