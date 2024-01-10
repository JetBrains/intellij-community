// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.j2k

import com.intellij.psi.PsiMethod
import org.jetbrains.kotlin.idea.j2k.DocCommentConverter

interface JavaToKotlinConverterServices {
    val referenceSearcher: ReferenceSearcher
    val superMethodsSearcher: SuperMethodsSearcher
    val resolverForConverter: ResolverForConverter
    val docCommentConverter: DocCommentConverter
    val javaDataFlowAnalyzerFacade: JavaDataFlowAnalyzerFacade
}

interface SuperMethodsSearcher {
    fun findDeepestSuperMethods(method: PsiMethod): Collection<PsiMethod>

    object Default : SuperMethodsSearcher {
        // use simple findSuperMethods by default because findDeepestSuperMethods requires some service from IDEA
        override fun findDeepestSuperMethods(method: PsiMethod) = method.findSuperMethods().asList()
    }
}
