// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.j2k

import com.intellij.psi.PsiMethod
import org.jetbrains.kotlin.idea.j2k.DocCommentConverter
import org.jetbrains.kotlin.idea.j2k.EmptyDocCommentConverter

interface JavaToKotlinConverterServices {
    val referenceSearcher: ReferenceSearcher
    val superMethodsSearcher: SuperMethodsSearcher
    val resolverForConverter: ResolverForConverter
    val docCommentConverter: DocCommentConverter
    val javaDataFlowAnalyzerFacade: JavaDataFlowAnalyzerFacade
}

object EmptyJavaToKotlinServices: JavaToKotlinConverterServices {
    override val referenceSearcher: ReferenceSearcher
        get() = EmptyReferenceSearcher

    override val superMethodsSearcher: SuperMethodsSearcher
        get() = SuperMethodsSearcher.Default

    override val resolverForConverter: ResolverForConverter
        get() = EmptyResolverForConverter

    override val docCommentConverter: DocCommentConverter
        get() = EmptyDocCommentConverter

    override val javaDataFlowAnalyzerFacade: JavaDataFlowAnalyzerFacade
        get() = JavaDataFlowAnalyzerFacade.Default
}

interface SuperMethodsSearcher {
    fun findDeepestSuperMethods(method: PsiMethod): Collection<PsiMethod>

    object Default : SuperMethodsSearcher {
        // use simple findSuperMethods by default because findDeepestSuperMethods requires some service from IDEA
        override fun findDeepestSuperMethods(method: PsiMethod) = method.findSuperMethods().asList()
    }
}
