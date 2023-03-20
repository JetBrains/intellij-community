// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.kotlin

import com.intellij.psi.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.asJava.elements.KtLightAbstractAnnotation
import org.jetbrains.kotlin.asJava.elements.KtLightElement
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.utils.SmartList
import org.jetbrains.uast.*
import org.jetbrains.uast.kotlin.internal.DelegatedMultiResolve
import org.jetbrains.uast.kotlin.psi.UastKotlinPsiParameter
import org.jetbrains.uast.kotlin.psi.UastKotlinPsiVariable

@ApiStatus.Internal
abstract class AbstractKotlinUVariable(
    givenParent: UElement?
) : KotlinAbstractUElement(givenParent), PsiVariable, UVariableEx, UAnchorOwner {

    override val uastInitializer: UExpression?
        get() {
            val initializerExpression = when (val psi = psi) {
                is UastKotlinPsiVariable -> psi.ktInitializer
                is UastKotlinPsiParameter -> psi.ktDefaultValue
                is KtLightElement<*, *> -> {
                    when (val origin = psi.kotlinOrigin?.takeIf { it.canAnalyze() }) { // EA-137191
                        is KtVariableDeclaration -> origin.initializer
                        is KtParameter -> origin.defaultValue
                        else -> null
                    }
                }
                else -> null
            } ?: return null
            return languagePlugin?.convertElement(initializerExpression, this) as? UExpression ?: UastEmptyExpression(null)
        }

    protected val delegateExpression: UExpression? by lz {
        val expression = when (val psi = psi) {
            is KtLightElement<*, *> -> (psi.kotlinOrigin as? KtProperty)?.delegateExpression
            is UastKotlinPsiVariable -> (psi.ktElement as? KtProperty)?.delegateExpression
            else -> null
        }

        expression?.let { languagePlugin?.convertElement(it, this) as? UExpression }
    }

    override fun getNameIdentifier(): PsiIdentifier {
        val kotlinOrigin = (psi as? KtLightElement<*, *>)?.kotlinOrigin
        return UastLightIdentifier(psi, kotlinOrigin as? KtDeclaration)
    }

    override fun getContainingFile(): PsiFile = unwrapFakeFileForLightClass(psi.containingFile)

    override val uAnnotations by lz {
        val sourcePsi = sourcePsi ?: return@lz psi.annotations.map { WrappedUAnnotation(it, this) }
        val annotations = SmartList<UAnnotation>(KotlinNullabilityUAnnotation(baseResolveProviderService, sourcePsi, this))
        if (sourcePsi is KtModifierListOwner) {
            sourcePsi.annotationEntries
                .filter { acceptsAnnotationTarget(it.useSiteTarget?.getAnnotationUseSiteTarget()) }
                .mapTo(annotations) { baseResolveProviderService.baseKotlinConverter.convertAnnotation(it, this) }
        }
        annotations
    }

    protected abstract fun acceptsAnnotationTarget(target: AnnotationUseSiteTarget?): Boolean

    override val typeReference: UTypeReferenceExpression? by lz {
        KotlinUTypeReferenceExpression((sourcePsi as? KtCallableDeclaration)?.typeReference, this) { type }
    }

    override val uastAnchor: UIdentifier?
        get() {
            val identifierSourcePsi = when (val sourcePsi = sourcePsi) {
                is KtNamedDeclaration -> sourcePsi.nameIdentifier
                is KtTypeReference -> sourcePsi.typeElement?.let {
                    // receiver param in extension function
                    (it as? KtUserType)?.referenceExpression?.getIdentifier() ?: it
                } ?: sourcePsi
                is KtNameReferenceExpression -> sourcePsi.getReferencedNameElement()
                is KtBinaryExpression, is KtCallExpression -> null // e.g. `foo("Lorem ipsum") ?: foo("dolor sit amet")`
                is KtDestructuringDeclaration -> sourcePsi.valOrVarKeyword
                is KtLambdaExpression -> sourcePsi.functionLiteral.lBrace
                else -> sourcePsi
            } ?: return null
            return KotlinUIdentifier(nameIdentifier, identifierSourcePsi, this)
        }

    override fun equals(other: Any?) = other is AbstractKotlinUVariable && psi == other.psi

    class WrappedUAnnotation(
        psiAnnotation: PsiAnnotation,
        override val uastParent: UElement
    ) : UAnnotation, UAnchorOwner, DelegatedMultiResolve {

        override val javaPsi: PsiAnnotation = psiAnnotation
        override val psi: PsiAnnotation = javaPsi
        override val sourcePsi: PsiElement? = (psiAnnotation as? KtLightAbstractAnnotation)?.kotlinOrigin

        override val attributeValues: List<UNamedExpression> by lz {
            psi.parameterList.attributes.map { WrappedUNamedExpression(it, this) }
        }

        override val uastAnchor: UIdentifier by lz {
            KotlinUIdentifier(
                { javaPsi.nameReferenceElement?.referenceNameElement },
                (sourcePsi as? KtAnnotationEntry)?.typeReference?.nameElement,
                this
            )
        }

        class WrappedUNamedExpression(
            pair: PsiNameValuePair,
            override val uastParent: UElement?
        ) : UNamedExpression {
            override val name: String? = pair.name
            override val psi = pair
            override val javaPsi: PsiElement = psi
            override val sourcePsi: PsiElement? = null
            override val uAnnotations: List<UAnnotation> = emptyList()
            override val expression: UExpression by lz { toUExpression(psi.value) }
        }

        override val qualifiedName: String? = psi.qualifiedName

        override fun findAttributeValue(name: String?): UExpression? =
            psi.findAttributeValue(name)?.let { toUExpression(it) }

        override fun findDeclaredAttributeValue(name: String?): UExpression? =
            psi.findDeclaredAttributeValue(name)?.let { toUExpression(it) }

        override fun resolve(): PsiClass? =
            psi.nameReferenceElement?.resolve() as? PsiClass
    }
}

private fun toUExpression(psi: PsiElement?): UExpression = psi.toUElementOfType() ?: UastEmptyExpression(null)
