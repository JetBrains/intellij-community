// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin

import com.intellij.lang.jvm.JvmModifier
import com.intellij.psi.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.asJava.elements.KtLightElement
import org.jetbrains.kotlin.asJava.elements.KtLightMethod
import org.jetbrains.kotlin.asJava.elements.isGetter
import org.jetbrains.kotlin.asJava.elements.isSetter
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.resolve.annotations.JVM_STATIC_ANNOTATION_FQ_NAME
import org.jetbrains.kotlin.utils.SmartList
import org.jetbrains.uast.*
import org.jetbrains.uast.kotlin.psi.UastKotlinPsiParameter

@ApiStatus.Internal
open class KotlinUMethod(
    psi: PsiMethod,
    final override val sourcePsi: KtDeclaration?,
    givenParent: UElement?
) : KotlinAbstractUElement(givenParent), UMethod, UAnchorOwner, PsiMethod by psi {
    constructor(
        psi: KtLightMethod,
        givenParent: UElement?
    ) : this(psi, getKotlinMemberOrigin(psi), givenParent)

    override val uastParameters: List<UParameter> by lz {

        fun parameterOrigin(psiParameter: PsiParameter?): KtElement? = when (psiParameter) {
            is KtLightElement<*, *> -> psiParameter.kotlinOrigin
            is UastKotlinPsiParameter -> psiParameter.ktParameter
            else -> null
        }

        val lightParams = psi.parameterList.parameters
        val receiver = receiverTypeReference ?: return@lz lightParams.map { KotlinUParameter(it, parameterOrigin(it), this) }
        val receiverLight = lightParams.firstOrNull() ?: return@lz emptyList()
        val uParameters = SmartList<UParameter>(KotlinReceiverUParameter(receiverLight, receiver, this))
        lightParams.drop(1).mapTo(uParameters) { KotlinUParameter(it, parameterOrigin(it), this) }
        uParameters
    }

    override val psi: PsiMethod = unwrap<UMethod, PsiMethod>(psi)

    override val javaPsi = psi

    override fun getSourceElement() = sourcePsi ?: this

    private val kotlinOrigin = getKotlinMemberOrigin(psi.originalElement) ?: sourcePsi

    override fun getContainingFile(): PsiFile? {
        kotlinOrigin?.containingFile?.let { return it }
        return unwrapFakeFileForLightClass(psi.containingFile)
    }

    override fun getNameIdentifier() = UastLightIdentifier(psi, kotlinOrigin)

    override val uAnnotations: List<UAnnotation> by lz {
        // NB: we can't use sourcePsi.annotationEntries directly due to annotation use-site targets. The given `psi` as a light element,
        // which spans regular function, property accessors, etc., is already built with targeted annotation.
        baseResolveProviderService.getPsiAnnotations(psi).asSequence()
            .filter { if (javaPsi.hasModifier(JvmModifier.STATIC)) !isJvmStatic(it) else true }
            .mapNotNull { (it as? KtLightElement<*, *>)?.kotlinOrigin as? KtAnnotationEntry }
            .map { baseResolveProviderService.baseKotlinConverter.convertAnnotation(it, this) }
            .toList()
    }

    private val receiverTypeReference by lz {
        when (sourcePsi) {
            is KtCallableDeclaration -> sourcePsi
            is KtPropertyAccessor -> sourcePsi.property
            else -> null
        }?.receiverTypeReference
    }

    override val uastAnchor: UIdentifier? by lz {
        val identifierSourcePsi = when (val sourcePsi = sourcePsi) {
            is PsiNameIdentifierOwner -> sourcePsi.nameIdentifier
            is KtObjectDeclaration -> sourcePsi.getObjectKeyword()
            is KtPropertyAccessor -> sourcePsi.namePlaceholder
            else -> sourcePsi?.navigationElement
        }
        KotlinUIdentifier(nameIdentifier, identifierSourcePsi, this)
    }

    override val uastBody: UExpression? by lz {
        if (kotlinOrigin?.canAnalyze() != true) return@lz null // EA-137193
        val bodyExpression = when (sourcePsi) {
            is KtFunction -> sourcePsi.bodyExpression
            is KtPropertyAccessor -> sourcePsi.bodyExpression
            is KtProperty -> when {
                psi is KtLightMethod && psi.isGetter -> sourcePsi.getter?.bodyExpression
                psi is KtLightMethod && psi.isSetter -> sourcePsi.setter?.bodyExpression
                else -> null
            }
            else -> null
        } ?: return@lz null

        wrapExpressionBody(this, bodyExpression)
    }

    override val returnTypeReference: UTypeReferenceExpression? by lz {
        (sourcePsi as? KtCallableDeclaration)?.typeReference?.let {
            KotlinUTypeReferenceExpression(it, this) { javaPsi.returnType ?: UastErrorType }
        }
    }

    companion object {
        fun create(
            psi: KtLightMethod,
            givenParent: UElement?
        ): UMethod {
            val kotlinOrigin = psi.kotlinOrigin
            return when {
                kotlinOrigin is KtConstructor<*> ->
                    KotlinConstructorUMethod(kotlinOrigin.containingClassOrObject, psi, givenParent)
                kotlinOrigin is KtParameter && kotlinOrigin.getParentOfType<KtClass>(true)?.isAnnotation() == true ->
                    KotlinUAnnotationMethod(psi, givenParent)
                else ->
                    KotlinUMethod(psi, givenParent)
            }
        }

        private fun isJvmStatic(it: PsiAnnotation): Boolean = it.hasQualifiedName(JVM_STATIC_ANNOTATION_FQ_NAME.asString())
    }
}
