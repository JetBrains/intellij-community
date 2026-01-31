// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.uast.kotlin

import com.intellij.lang.jvm.JvmModifier
import com.intellij.psi.NavigatablePsiElement
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiTypes
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.asJava.elements.KtLightElement
import org.jetbrains.kotlin.asJava.elements.KtLightMethod
import org.jetbrains.kotlin.asJava.elements.isGetter
import org.jetbrains.kotlin.asJava.elements.isSetter
import org.jetbrains.kotlin.name.JvmStandardClassIds.JVM_STATIC_FQ_NAME
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtConstructor
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.psi.psiUtil.quoteIfNeeded
import org.jetbrains.kotlin.utils.SmartList
import org.jetbrains.kotlin.utils.addIfNotNull
import org.jetbrains.uast.UAnchorOwner
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UIdentifier
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UParameter
import org.jetbrains.uast.UTypeReferenceExpression
import org.jetbrains.uast.UastErrorType
import org.jetbrains.uast.UastLazyPart
import org.jetbrains.uast.getOrBuild
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

    private val psiRef: PsiMethod = psi

    private val receiverTypeReferencePart = UastLazyPart<KtTypeReference?>()
    private val uastParametersPart = UastLazyPart<List<UParameter>>()
    private val uastAnchorPart = UastLazyPart<UIdentifier?>()
    private val uAnnotationsPart = UastLazyPart<List<UAnnotation>>()
    private val uastBodyPart = UastLazyPart<UExpression?>()
    private val returnTypeReferencePart = UastLazyPart<UTypeReferenceExpression?>()

    override val uastParameters: List<UParameter>
        get() = uastParametersPart.getOrBuild {
            fun parameterOrigin(psiParameter: PsiParameter?): KtElement? = when (psiParameter) {
                is KtLightElement<*, *> -> psiParameter.kotlinOrigin
                is UastKotlinPsiParameter -> psiParameter.ktParameter
                else -> null
            }

            val lightParams = psi.parameterList.parameters
            val receiver = receiverTypeReference ?: return@getOrBuild lightParams.map { KotlinUParameter(it, parameterOrigin(it), this) }
            val receiverLight = lightParams.firstOrNull() ?: return@getOrBuild emptyList()
            val uParameters = SmartList<UParameter>(KotlinReceiverUParameter(receiverLight, receiver, this))
            lightParams.drop(1).mapTo(uParameters) { KotlinUParameter(it, parameterOrigin(it), this) }
            uParameters
        }

    override val psi: PsiMethod = unwrap<UMethod, PsiMethod>(psi)

    override val javaPsi: PsiMethod = psi

    override fun getSourceElement(): NavigatablePsiElement = sourcePsi ?: this

    private val kotlinOrigin = getKotlinMemberOrigin(psi.originalElement) ?: sourcePsi

    override fun getContainingFile(): PsiFile? {
        kotlinOrigin?.containingFile?.let { return it }
        return unwrapFakeFileForLightClass(psi.containingFile)
    }

    override fun getNameIdentifier(): UastLightIdentifier = UastLightIdentifier(psi, kotlinOrigin)

    override val uAnnotations: List<UAnnotation>
        get() = uAnnotationsPart.getOrBuild {
            // NB: we can't use sourcePsi.annotationEntries directly due to annotation use-site targets. The given `psi` as a light element,
            // which spans regular function, property accessors, etc., is already built with targeted annotation.
            baseResolveProviderService.getPsiAnnotations(psi).asSequence()
                .filter { if (javaPsi.hasModifier(JvmModifier.STATIC)) !isJvmStatic(it) else true }
                .mapNotNull { (it as? KtLightElement<*, *>)?.kotlinOrigin as? KtAnnotationEntry }
                .map { baseResolveProviderService.baseKotlinConverter.convertAnnotation(it, this) }
                .toList()
        }

    private val receiverTypeReference: KtTypeReference?
        get() = receiverTypeReferencePart.getOrBuild {
            when (sourcePsi) {
                is KtCallableDeclaration -> sourcePsi
                is KtPropertyAccessor -> sourcePsi.property
                else -> null
            }?.receiverTypeReference
        }

    override val uastAnchor: UIdentifier?
        get() = uastAnchorPart.getOrBuild {
            val identifierSourcePsi = when (val sourcePsi = sourcePsi) {
                is PsiNameIdentifierOwner -> sourcePsi.nameIdentifier
                is KtPropertyAccessor -> sourcePsi.namePlaceholder
                else -> sourcePsi?.navigationElement
            }
            KotlinUIdentifier({ nameIdentifier }, identifierSourcePsi, this)
        }

    internal var jvmOverload: UMethod? = null

    protected fun buildTrampolineForJvmOverload(): UExpression? {
        if (jvmOverload == null) return null
        var currentMethodParameterIndex = 0
        val callArguments = mutableListOf<String>()
        for (uParam in jvmOverload!!.uastParameters) {
            val currentMethodParameter = uastParameters.getOrNull(currentMethodParameterIndex++)
            // Skip the extension receiver
            if (currentMethodParameter is KotlinReceiverUParameter) {
                continue
            }
            if (currentMethodParameter?.name == uParam.name) {
                callArguments.add(currentMethodParameter.name)
            } else {
                callArguments.addIfNotNull(uParam.uastInitializer?.sourcePsi?.text)
            }
        }
        val trampolineText =
            buildString {
                if (!isConstructor && returnType != PsiTypes.voidType()) {
                    append("return ")
                }
                append(jvmOverload!!.name.quoteIfNeeded())
                callArguments.joinTo(this, prefix = "(", postfix = ")", separator = ", ")
            }
        val trampoline = KtPsiFactory.contextual(sourcePsi ?: javaPsi).createExpression(trampolineText)
        return KotlinLazyUBlockExpression(this) { uastParent ->
            listOf(
                baseResolveProviderService.baseKotlinConverter.convertOrEmpty(trampoline, uastParent)
            )
        }
    }

    override val uastBody: UExpression?
        get() = uastBodyPart.getOrBuild {
            if (kotlinOrigin?.canAnalyze() != true) return@getOrBuild null // EA-137193

            buildTrampolineForJvmOverload()?.let { return it }

            val bodyExpression = when (sourcePsi) {
                is KtFunction, is KtPropertyAccessor -> sourcePsi.bodyExpressionIfNotCompiled
                is KtProperty -> when (psiRef) {
                    is KtLightMethod if psiRef.isGetter -> sourcePsi.getter?.bodyExpressionIfNotCompiled
                    is KtLightMethod if psiRef.isSetter -> sourcePsi.setter?.bodyExpressionIfNotCompiled
                    else -> null
                }

                else -> null
            } ?: return@getOrBuild null

            wrapExpressionBody(this, bodyExpression)
        }

    override val returnTypeReference: UTypeReferenceExpression?
        get() = returnTypeReferencePart.getOrBuild {
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
                kotlinOrigin is KtConstructor<*> && psi.isConstructor ->
                    KotlinConstructorUMethod(kotlinOrigin.containingClassOrObject, psi, givenParent)

                kotlinOrigin is KtParameter && kotlinOrigin.getParentOfType<KtClass>(true)?.isAnnotation() == true ->
                    KotlinUAnnotationMethod(psi, givenParent)

                else ->
                    KotlinUMethod(psi, givenParent)
            }
        }

        private fun isJvmStatic(it: PsiAnnotation): Boolean = it.hasQualifiedName(JVM_STATIC_FQ_NAME.asString())
    }
}
