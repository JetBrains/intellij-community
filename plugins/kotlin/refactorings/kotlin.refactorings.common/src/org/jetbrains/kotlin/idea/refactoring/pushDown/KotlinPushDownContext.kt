// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.pushDown

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.refactoring.memberInfo.KotlinMemberInfo
import org.jetbrains.kotlin.psi.KtClass

@ApiStatus.Internal
abstract class KotlinPushDownContext(
    val sourceClass: KtClass,
    val membersToMove: List<KotlinMemberInfo>
)
