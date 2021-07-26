// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.kotlin

import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.ResolveResult
import org.jetbrains.kotlin.asJava.toLightAnnotation
import org.jetbrains.kotlin.descriptors.ClassConstructorDescriptor
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.idea.util.actionUnderSafeAnalyzeBlock
import org.jetbrains.kotlin.name.FqNameUnsafe
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.callUtil.getResolvedCall
import org.jetbrains.kotlin.resolve.descriptorUtil.annotationClass
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameUnsafe
import org.jetbrains.kotlin.resolve.lazy.ForceResolveUtil
import org.jetbrains.kotlin.resolve.source.getPsi
import org.jetbrains.kotlin.types.ErrorUtils
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import org.jetbrains.uast.*
import org.jetbrains.uast.kotlin.internal.multiResolveResults

abstract class KotlinUAnnotationBase<T : KtCallElement>(
    final override val sourcePsi: T,
    givenParent: UElement?
) : KotlinAbstractUElement(givenParent), UAnnotationEx, UAnchorOwner, UMultiResolvable {

    abstract override val javaPsi: PsiAnnotation?

    final override val psi: PsiElement = sourcePsi

    protected abstract fun annotationUseSiteTarget(): AnnotationUseSiteTarget?

    override val qualifiedName: String? by lz {
        computeClassDescriptor().takeUnless(ErrorUtils::isError)
            ?.fqNameUnsafe
            ?.takeIf(FqNameUnsafe::isSafe)
            ?.toSafe()
            ?.toString()
    }

    override val attributeValues: List<UNamedExpression> by lz {
        baseResolveProviderService.convertValueArguments(sourcePsi, this) ?: emptyList()
    }

    protected abstract fun computeClassDescriptor(): ClassDescriptor?

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
        val parameter = computeClassDescriptor()
            ?.unsubstitutedPrimaryConstructor
            ?.valueParameters
            ?.find { it.name.asString() == name } ?: return null

        val defaultValue = (parameter.source.getPsi() as? KtParameter)?.defaultValue ?: return null
        return languagePlugin?.convertWithParent(defaultValue)
    }

    override fun convertParent(): UElement? {
        sourcePsi.parent.safeAs<KtAnnotatedExpression>()?.let { annotatedExpression ->
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

class KotlinUAnnotation(
    annotationEntry: KtAnnotationEntry,
    givenParent: UElement?
) : KotlinUAnnotationBase<KtAnnotationEntry>(annotationEntry, givenParent), UAnnotation {

    override val javaPsi: PsiAnnotation? =
        annotationEntry.actionUnderSafeAnalyzeBlock({ annotationEntry.toLightAnnotation() }, { null })

    override fun computeClassDescriptor(): ClassDescriptor? =
        sourcePsi.analyze()[BindingContext.ANNOTATION, sourcePsi]?.annotationClass

    override fun annotationUseSiteTarget() = sourcePsi.useSiteTarget?.getAnnotationUseSiteTarget()

    override fun resolve(): PsiClass? {
        return baseResolveProviderService.resolveToClass(sourcePsi)
    }

    override val uastAnchor by lz {
        KotlinUIdentifier(
            javaPsi?.nameReferenceElement,
            annotationEntry.typeReference?.nameElement,
            this
        )
    }

}

class KotlinUNestedAnnotation private constructor(
    original: KtCallExpression,
    givenParent: UElement?
) : KotlinUAnnotationBase<KtCallExpression>(original, givenParent) {
    override val javaPsi: PsiAnnotation? by lz { original.toLightAnnotation() }

    override fun computeClassDescriptor(): ClassDescriptor? = classDescriptor(sourcePsi)

    override fun annotationUseSiteTarget(): AnnotationUseSiteTarget? = null

    override fun resolve(): PsiClass? {
        return baseResolveProviderService.resolveToClassIfConstructorCall(sourcePsi, this)
    }

    override val uastAnchor by lz {
        KotlinUIdentifier(
            javaPsi?.nameReferenceElement?.referenceNameElement,
            (original.calleeExpression as? KtNameReferenceExpression)?.getReferencedNameElement(),
            this
        )
    }

    companion object {
        fun tryCreate(original: KtCallExpression, givenParent: UElement?): KotlinUNestedAnnotation? {
            if (classDescriptor(original)?.kind == ClassKind.ANNOTATION_CLASS)
                return KotlinUNestedAnnotation(original, givenParent)
            else
                return null
        }

        private fun classDescriptor(original: KtCallExpression) =
            (original.getResolvedCall(original.analyze())?.resultingDescriptor as? ClassConstructorDescriptor)?.constructedClass
    }

}


