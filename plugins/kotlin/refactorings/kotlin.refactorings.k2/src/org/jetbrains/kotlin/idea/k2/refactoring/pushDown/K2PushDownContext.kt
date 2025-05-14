// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.pushDown

import org.jetbrains.kotlin.idea.refactoring.memberInfo.KotlinMemberInfo
import org.jetbrains.kotlin.idea.refactoring.pushDown.KotlinPushDownContext
import org.jetbrains.kotlin.psi.KtClass

class K2PushDownContext(
    sourceClass: KtClass,
    membersToMove: List<KotlinMemberInfo>,
) : KotlinPushDownContext(sourceClass, membersToMove)
