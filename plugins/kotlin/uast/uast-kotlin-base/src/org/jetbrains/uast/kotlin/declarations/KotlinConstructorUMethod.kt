// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin

import com.intellij.psi.PsiMethod
import org.jetbrains.kotlin.asJava.elements.KtLightMethod
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.utils.SmartList
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UIdentifier

open class KotlinConstructorUMethod(
    private val ktClass: KtClassOrObject?,
    override val psi: PsiMethod,
    kotlinOrigin: KtDeclaration?,
    givenParent: UElement?
) : BaseKotlinUMethod(psi, kotlinOrigin, givenParent) {

    constructor(
        ktClass: KtClassOrObject?,
        psi: KtLightMethod,
        givenParent: UElement?
    ) : this(ktClass, psi, psi.kotlinOrigin, givenParent)

    override val javaPsi = psi

    val isPrimary: Boolean
        get() = sourcePsi is KtPrimaryConstructor || sourcePsi is KtClassOrObject

    override val uastBody: UExpression? by lz {
        val delegationCall: KtCallElement? = sourcePsi.let {
            when {
                isPrimary -> ktClass?.superTypeListEntries?.firstIsInstanceOrNull<KtSuperTypeCallEntry>()
                it is KtSecondaryConstructor -> it.getDelegationCall()
                else -> null
            }
        }
        val bodyExpressions = getBodyExpressions()
        if (delegationCall == null && bodyExpressions.isEmpty()) return@lz null
        KotlinLazyUBlockExpression(this) { uastParent ->
            SmartList<UExpression>().apply {
                delegationCall?.let {
                    add(KotlinUFunctionCallExpression(it, uastParent))
                }
                bodyExpressions.forEach {
                    add(baseResolveProviderService.baseKotlinConverter.convertOrEmpty(it, uastParent))
                }
            }
        }
    }

    override val uastAnchor: UIdentifier? by lz {
        KotlinUIdentifier(
            javaPsi.nameIdentifier,
            if (isPrimary) ktClass?.nameIdentifier else (sourcePsi as? KtSecondaryConstructor)?.getConstructorKeyword(),
            this
        )
    }

    protected open fun getBodyExpressions(): List<KtExpression> {
        if (isPrimary) return getInitializers()
        val bodyExpression = (sourcePsi as? KtFunction)?.bodyExpression ?: return emptyList()
        if (bodyExpression is KtBlockExpression) return bodyExpression.statements
        return listOf(bodyExpression)
    }

    protected fun getInitializers(): List<KtExpression> {
        return ktClass?.getAnonymousInitializers()?.mapNotNull { it.body } ?: emptyList()
    }
}
