// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.usages

import com.intellij.psi.PsiElement
import com.intellij.usageView.UsageInfo
import org.jetbrains.kotlin.psi.KtCallableDeclaration

internal class KotlinOverrideUsageInfo(overrider: KtCallableDeclaration, val baseMethod: PsiElement) : UsageInfo(overrider)