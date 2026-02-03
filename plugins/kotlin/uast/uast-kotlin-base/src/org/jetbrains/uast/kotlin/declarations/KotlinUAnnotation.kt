// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.kotlin

import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.ResolveResult
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.psi.KtAnnotatedExpression
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull
import org.jetbrains.uast.DEFAULT_EXPRESSION_TYPES_LIST
import org.jetbrains.uast.UAnchorOwner
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UAnnotationEx
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UIdentifier
import org.jetbrains.uast.UMultiResolvable
import org.jetbrains.uast.UNINITIALIZED_UAST_PART
import org.jetbrains.uast.UNamedExpression
import org.jetbrains.uast.UastLazyPart
import org.jetbrains.uast.getOrBuild
import org.jetbrains.uast.kotlin.internal.multiResolveResults

@ApiStatus.Internal
sealed class KotlinUAnnotationBase<T : KtCallElement>(
    final override val sourcePsi: T,
    givenParent: UElement?
) : KotlinAbstractUElement(givenParent), UAnnotationEx, UAnchorOwner, UMultiResolvable {

    private val attributeValuesPart = UastLazyPart<List<UNamedExpression>>()
    private var qualifiedNameValue: Any? = UNINITIALIZED_UAST_PART

    abstract override val javaPsi: PsiAnnotation?

    final override val psi: PsiElement = sourcePsi

    protected abstract fun annotationUseSiteTarget(): AnnotationUseSiteTarget?

    override val qualifiedName: String?
        get() {
            if (qualifiedNameValue == UNINITIALIZED_UAST_PART) {
                qualifiedNameValue = baseResolveProviderService.qualifiedAnnotationName(sourcePsi)
            }
            return qualifiedNameValue as String?
        }

    override val attributeValues: List<UNamedExpression>
        get() = attributeValuesPart.getOrBuild {
            baseResolveProviderService.convertValueArguments(sourcePsi, this) ?: emptyList()
        }

    override fun findAttributeValue(name: String?): UExpression? =
        findDeclaredAttributeValue(name) ?: findAttributeDefaultValue(name ?: "value")

    override fun findDeclaredAttributeValue(name: String?): UExpression? {
        return attributeValues.find {
            it.name == name ||
                    (name == null && it.name == "value") ||
                    (name == "value" && it.name == null)
        }?.expression
    }

    private fun findAttributeDefaultValue(name: String): UExpression? {
        return baseResolveProviderService.findDefaultValueForAnnotationAttribute(sourcePsi, name)
    }

    override fun convertParent(): UElement? {
        sourcePsi.parentAs<KtAnnotatedExpression>()?.let { annotatedExpression ->
            return annotatedExpression.baseExpression?.let {
                baseResolveProviderService.baseKotlinConverter.convertExpression(it, null, DEFAULT_EXPRESSION_TYPES_LIST)
            }
        }

        val superParent = super.convertParent() ?: return null
        if (annotationUseSiteTarget() == AnnotationUseSiteTarget.RECEIVER) {
            (superParent.uastParent as? KotlinUMethod)?.uastParameters?.firstIsInstanceOrNull<KotlinReceiverUParameter>()?.let {
                return it
            }
        }
        return superParent
    }

    override fun multiResolve(): Iterable<ResolveResult> = sourcePsi.multiResolveResults().asIterable()
}

@ApiStatus.Internal
class KotlinUAnnotation(
    private val annotationEntry: KtAnnotationEntry,
    givenParent: UElement?
) : KotlinUAnnotationBase<KtAnnotationEntry>(annotationEntry, givenParent), UAnnotation {

    private val javaPsiPart = UastLazyPart<PsiAnnotation?>()
    private val uastAnchorPart = UastLazyPart<UIdentifier>()

    override val javaPsi: PsiAnnotation?
        get() = javaPsiPart.getOrBuild {
            baseResolveProviderService.convertToPsiAnnotation(annotationEntry)
        }

    override fun annotationUseSiteTarget(): AnnotationUseSiteTarget? = sourcePsi.useSiteTarget?.getAnnotationUseSiteTarget()

    override fun resolve(): PsiClass? {
        return baseResolveProviderService.resolveToClass(sourcePsi, this)
    }

    override val uastAnchor: UIdentifier
        get() = uastAnchorPart.getOrBuild {
            KotlinUIdentifier(
                { javaPsi?.nameReferenceElement },
                annotationEntry.typeReference?.nameElement,
                this
            )
        }
}

@ApiStatus.Internal
class KotlinUNestedAnnotation private constructor(
    private val original: KtCallExpression,
    givenParent: UElement?
) : KotlinUAnnotationBase<KtCallExpression>(original, givenParent) {

    private val javaPsiPart = UastLazyPart<PsiAnnotation?>()
    private val uastAnchorPart = UastLazyPart<UIdentifier>()

    override val javaPsi: PsiAnnotation?
        get() = javaPsiPart.getOrBuild {
            baseResolveProviderService.convertToPsiAnnotation(original)
        }

    override fun annotationUseSiteTarget(): AnnotationUseSiteTarget? = null

    override fun resolve(): PsiClass? {
        return baseResolveProviderService.resolveToClassIfConstructorCall(sourcePsi, this)
    }

    override val uastAnchor: UIdentifier
        get() = uastAnchorPart.getOrBuild {
            KotlinUIdentifier(
                { javaPsi?.nameReferenceElement?.referenceNameElement },
                (original.calleeExpression as? KtNameReferenceExpression)?.getReferencedNameElement(),
                this
            )
        }

    companion object {
        fun create(ktCallExpression: KtCallExpression, givenParent: UElement?): KotlinUNestedAnnotation? {
            val service = ApplicationManager.getApplication().getService(BaseKotlinUastResolveProviderService::class.java)
            return if (service.isAnnotationConstructorCall(ktCallExpression))
                KotlinUNestedAnnotation(ktCallExpression, givenParent)
            else
                null
        }
    }

}
