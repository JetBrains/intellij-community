// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast.kotlin

import com.intellij.psi.PsiMethod
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.asJava.elements.KtLightMethod
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtPrimaryConstructor
import org.jetbrains.kotlin.psi.KtSecondaryConstructor
import org.jetbrains.kotlin.psi.KtSuperTypeCallEntry
import org.jetbrains.kotlin.utils.SmartList
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UIdentifier
import org.jetbrains.uast.UastLazyPart
import org.jetbrains.uast.getOrBuild

@ApiStatus.Internal
open class KotlinConstructorUMethod(
    private val ktClass: KtClassOrObject?,
    override val psi: PsiMethod,
    kotlinOrigin: KtDeclaration?,
    givenParent: UElement?
) : KotlinUMethod(psi, kotlinOrigin, givenParent) {

    constructor(
        ktClass: KtClassOrObject?,
        psi: KtLightMethod,
        givenParent: UElement?
    ) : this(ktClass, psi, psi.kotlinOrigin, givenParent)

    private val uastBodyPart = UastLazyPart<UExpression?>()
    private val uastAnchorPart = UastLazyPart<UIdentifier?>()

    override val javaPsi: PsiMethod = psi

    internal val isPrimary: Boolean
        get() = sourcePsi is KtPrimaryConstructor || sourcePsi is KtClassOrObject

    override val uastBody: UExpression?
        get() = uastBodyPart.getOrBuild {
            buildTrampolineForJvmOverload()?.let { return it }

            val delegationCall: KtCallElement? = sourcePsi.let {
                when {
                    isPrimary -> ktClass?.superTypeListEntries?.firstIsInstanceOrNull<KtSuperTypeCallEntry>()
                    it is KtSecondaryConstructor -> it.getDelegationCall()
                    else -> null
                }
            }
            val bodyExpressions = getBodyExpressions()
            if (delegationCall == null && bodyExpressions.isEmpty()) return@getOrBuild null
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

    override val uastAnchor: UIdentifier?
        get() = uastAnchorPart.getOrBuild {
            KotlinUIdentifier(
                { javaPsi.nameIdentifier },
                if (isPrimary) ktClass?.nameIdentifier else (sourcePsi as? KtSecondaryConstructor)?.getConstructorKeyword(),
                this
            )
        }

    protected open fun getBodyExpressions(): List<KtExpression> {
        if (isPrimary) return getInitializers()
        val bodyExpression = (sourcePsi as? KtFunction)?.bodyExpressionIfNotCompiled ?: return emptyList()
        if (bodyExpression is KtBlockExpression) return bodyExpression.statements
        return listOf(bodyExpression)
    }

    protected fun getInitializers(): List<KtExpression> {
        return ktClass?.getAnonymousInitializers()?.mapNotNull { it.body } ?: emptyList()
    }
}
