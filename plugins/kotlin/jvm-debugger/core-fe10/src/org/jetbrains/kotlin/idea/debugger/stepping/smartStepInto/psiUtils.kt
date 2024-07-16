// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.stepping.smartStepInto

import org.jetbrains.kotlin.psi.KtDeclaration

internal fun areElementsEquivalent(element1: KtDeclaration, element2: KtDeclaration): Boolean {
    val psiManager = element1.manager
    if (psiManager.areElementsEquivalent(element1, element2)) return true
    // fast path
    if (element1.name != element2.name) return false
    // One of the elements might be from the decompiled code, so the elements will not match.
    return psiManager.areElementsEquivalent(element1.navigationElement, element2.navigationElement)
}
