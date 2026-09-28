// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.junit

import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClass

internal fun KtClass.hasPublicConstructor(): Boolean {
    val constructors = listOfNotNull(primaryConstructor) + secondaryConstructors
    return constructors.isEmpty() || constructors.any {
        !it.hasModifier(KtTokens.PRIVATE_KEYWORD) &&
                !it.hasModifier(KtTokens.PROTECTED_KEYWORD) &&
                !it.hasModifier(KtTokens.INTERNAL_KEYWORD)
    }
}
