// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.j2k

import com.intellij.psi.PsiMethod

object SuperMethodsSearcher {
    fun findDeepestSuperMethods(method: PsiMethod): Collection<PsiMethod> =
        method.findDeepestSuperMethods().asList()
}
