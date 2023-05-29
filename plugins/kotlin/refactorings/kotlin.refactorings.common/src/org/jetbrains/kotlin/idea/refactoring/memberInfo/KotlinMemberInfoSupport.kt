// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.memberInfo

import com.intellij.openapi.components.service
import org.jetbrains.kotlin.psi.KtNamedDeclaration

interface KotlinMemberInfoSupport {
    companion object {
        @JvmStatic
        fun getInstance(): KotlinMemberInfoSupport = service()
    }

    fun getOverrides(member: KtNamedDeclaration): Boolean?
}