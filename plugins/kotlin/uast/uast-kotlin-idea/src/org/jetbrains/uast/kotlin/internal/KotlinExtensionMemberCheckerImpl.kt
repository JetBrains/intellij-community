// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast.kotlin.internal

import com.intellij.psi.PsiModifierListOwner
import com.siyeh.ig.dependency.KotlinExtensionMemberChecker
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.kotlin.KotlinReceiverUParameter
import org.jetbrains.uast.toUElement

internal class KotlinExtensionMemberCheckerImpl : KotlinExtensionMemberChecker {
    override fun check(target: PsiModifierListOwner): Boolean {
        val uMethod = target.toUElement(UMethod::class.java) ?: return false
        return uMethod.uastParameters.firstOrNull() is KotlinReceiverUParameter
    }
}
