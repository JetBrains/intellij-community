// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.kotlin

import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import org.jetbrains.kotlin.analysis.api.types.KtTypeNullability
import org.jetbrains.uast.*
import org.jetbrains.uast.kotlin.internal.DelegatedMultiResolve

@ApiStatus.Internal
class KotlinNullabilityUAnnotation(
  private val baseKotlinUastResolveProviderService: BaseKotlinUastResolveProviderService,
  private val annotatedElement: PsiElement,
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

    private val nullability : KtTypeNullability? by lz {
        baseKotlinUastResolveProviderService.nullability(annotatedElement)
    }

    override val qualifiedName: String?
        get() = when (nullability) {
            KtTypeNullability.NON_NULLABLE -> NotNull::class.qualifiedName
            KtTypeNullability.NULLABLE -> Nullable::class.qualifiedName
            KtTypeNullability.UNKNOWN -> null
            null -> null
        }

    override fun findAttributeValue(name: String?): UExpression? = null

    override fun findDeclaredAttributeValue(name: String?): UExpression? = null

    private val _resolved: PsiClass? by lz {
        qualifiedName?.let {
            val project = annotatedElement.project
            JavaPsiFacade.getInstance(project).findClass(it, GlobalSearchScope.allScope(project))
        }
    }

    override fun resolve(): PsiClass? = _resolved
}
