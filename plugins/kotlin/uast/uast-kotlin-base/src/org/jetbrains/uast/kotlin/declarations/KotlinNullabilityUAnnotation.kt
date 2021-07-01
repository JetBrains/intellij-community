// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.kotlin

import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import org.jetbrains.kotlin.types.typeUtil.TypeNullability
import org.jetbrains.uast.*
import org.jetbrains.uast.kotlin.internal.DelegatedMultiResolve

class KotlinNullabilityUAnnotation(
    private val baseKotlinUastResolveProviderService: BaseKotlinUastResolveProviderService,
    val annotatedElement: PsiElement,
    override val uastParent: UElement?
) : UAnnotationEx, UAnchorOwner, DelegatedMultiResolve {

    override val uastAnchor: UIdentifier? = null

    override val attributeValues: List<UNamedExpression>
        get() = emptyList()
    override val psi: PsiElement?
        get() = null
    override val javaPsi: PsiAnnotation?
        get() = null
    override val sourcePsi: PsiElement?
        get() = null
    override val qualifiedName: String?
        get() = when (baseKotlinUastResolveProviderService.nullability(annotatedElement)) {
            TypeNullability.NOT_NULL -> NotNull::class.qualifiedName
            TypeNullability.NULLABLE -> Nullable::class.qualifiedName
            TypeNullability.FLEXIBLE -> null
            null -> null
        }

    override fun findAttributeValue(name: String?): UExpression? = null

    override fun findDeclaredAttributeValue(name: String?): UExpression? = null

    override fun resolve(): PsiClass? = qualifiedName?.let {
        val project = annotatedElement.project
        JavaPsiFacade.getInstance(project).findClass(it, GlobalSearchScope.allScope(project))
    }
}
