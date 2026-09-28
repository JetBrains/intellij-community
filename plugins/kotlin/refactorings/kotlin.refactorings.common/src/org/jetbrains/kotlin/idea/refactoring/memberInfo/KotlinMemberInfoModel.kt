// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.memberInfo

import com.intellij.refactoring.classMembers.MemberInfoModel
import org.jetbrains.kotlin.psi.KtElement

interface KotlinMemberInfoModel<I: KotlinMemberInfo> : MemberInfoModel<KtElement, I>

