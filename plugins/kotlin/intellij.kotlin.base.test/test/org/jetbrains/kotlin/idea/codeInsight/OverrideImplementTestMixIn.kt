// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight

import com.intellij.codeInsight.generation.ClassMember
import org.jetbrains.kotlin.idea.core.overrideImplement.AbstractGenerateMembersHandler
import org.jetbrains.kotlin.psi.KtClassOrObject

interface OverrideImplementTestMixIn<T : ClassMember> {
    fun createImplementMembersHandler(): AbstractGenerateMembersHandler<T>
    fun createOverrideMembersHandler(): AbstractGenerateMembersHandler<T>
    fun isMemberOfAny(parentClass: KtClassOrObject, chooserObject: T): Boolean
    fun getMemberName(parentClass: KtClassOrObject, chooserObject: T): String
    fun getContainingClassName(parentClass: KtClassOrObject, chooserObject: T): String
}