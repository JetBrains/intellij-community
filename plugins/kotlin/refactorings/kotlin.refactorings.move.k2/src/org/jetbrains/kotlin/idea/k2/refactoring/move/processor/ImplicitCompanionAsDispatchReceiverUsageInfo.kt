// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move.processor

import com.intellij.usageView.UsageInfo
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtSimpleNameExpression

class ImplicitCompanionAsDispatchReceiverUsageInfo(
    callee: KtSimpleNameExpression,
    val companionObject: KtObjectDeclaration
) : UsageInfo(callee)
