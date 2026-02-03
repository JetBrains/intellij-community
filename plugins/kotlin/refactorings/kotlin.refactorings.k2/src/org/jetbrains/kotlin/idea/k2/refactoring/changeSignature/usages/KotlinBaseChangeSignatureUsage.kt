// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.usages

import com.intellij.usageView.UsageInfo
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.KotlinChangeInfoBase
import org.jetbrains.kotlin.psi.KtElement

interface KotlinBaseChangeSignatureUsage {
    fun processUsage(changeInfo: KotlinChangeInfoBase, element: KtElement, allUsages: Array<out UsageInfo>): KtElement?
}

internal class KotlinChangeSignatureConflictingUsageInfo(element: KtElement, val conflictMessage: String) : UsageInfo(element) {}