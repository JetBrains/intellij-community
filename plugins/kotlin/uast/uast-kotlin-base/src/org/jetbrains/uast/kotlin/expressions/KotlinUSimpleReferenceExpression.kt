/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.uast.kotlin

import com.intellij.openapi.util.Key
import com.intellij.psi.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.findAssignment
import org.jetbrains.uast.*
import org.jetbrains.uast.internal.acceptList
import org.jetbrains.uast.kotlin.internal.DelegatedMultiResolve
import org.jetbrains.uast.visitor.UastVisitor

var PsiElement.destructuringDeclarationInitializer: Boolean? by UserDataProperty(Key.create("kotlin.uast.destructuringDeclarationInitializer"))

@ApiStatus.Internal
class KotlinUSimpleReferenceExpression(
    override val sourcePsi: KtSimpleNameExpression,
    givenParent: UElement?
) : KotlinAbstractUExpression(givenParent), USimpleNameReferenceExpression, KotlinUElementWithType, KotlinEvaluatableUElement {

    private val resolvedDeclaration: PsiElement? by lz {
        baseResolveProviderService.resolveToDeclaration(sourcePsi)
    }

    override val identifier get() = sourcePsi.getReferencedName()

    override fun resolve() = resolvedDeclaration

    override val resolvedName: String?
        get() = (resolvedDeclaration as? PsiNamedElement)?.name

    override val referenceNameElement: UElement? by lz { sourcePsi.getIdentifier()?.toUElement() }

    override fun accept(visitor: UastVisitor) {
        visitor.visitSimpleNameReferenceExpression(this)

        if (sourcePsi.parent.destructuringDeclarationInitializer != true) {
            visitAccessorCalls(visitor)
        }
        uAnnotations.acceptList(visitor)

        visitor.afterVisitSimpleNameReferenceExpression(this)
    }

    private fun visitAccessorCalls(visitor: UastVisitor) {
        // Visit Kotlin get-set synthetic Java property calls as function calls
        val resolvedMethod = baseResolveProviderService.resolveSyntheticJavaPropertyAccessorCall(sourcePsi) ?: return
        val access = sourcePsi.readWriteAccess()
        val setterValue = if (access.isWrite) {
            findAssignment(sourcePsi)?.right ?: run {
                visitor.afterVisitSimpleNameReferenceExpression(this)
                return
            }
        } else {
            null
        }

        if (access.isRead) {
            KotlinAccessorCallExpression(sourcePsi, this, resolvedMethod, null).accept(visitor)
        }

        if (access.isWrite && setterValue != null) {
            KotlinAccessorCallExpression(sourcePsi, this, resolvedMethod, setterValue).accept(visitor)
        }
    }

    @ApiStatus.Internal
    class KotlinAccessorCallExpression(
        override val sourcePsi: KtSimpleNameExpression,
        givenParent: KotlinUSimpleReferenceExpression,
        private val resolvedMethod: PsiMethod,
        val setterValue: KtExpression?
    ) : KotlinAbstractUExpression(givenParent), UCallExpression, DelegatedMultiResolve {
        override val methodName: String
            get() = resolvedMethod.name

        override val receiver: UExpression?
            get() {
                val containingElement = uastParent?.uastParent
                return if (containingElement is UQualifiedReferenceExpression && containingElement.selector == this)
                    containingElement.receiver
                else
                    null
            }

        override val javaPsi: PsiElement? get() = null
        override val psi: PsiElement get() = sourcePsi

        override val uAnnotations: List<UAnnotation>
            get() = emptyList()

        override val receiverType by lz {
            baseResolveProviderService.getAccessorReceiverType(sourcePsi, this)
        }

        override val methodIdentifier: UIdentifier? by lz {
            KotlinUIdentifier(sourcePsi.getReferencedNameElement(), this)
        }

        override val classReference: UReferenceExpression?
            get() = null

        override val valueArgumentCount: Int
            get() = if (setterValue != null) 1 else 0

        override val valueArguments by lz {
            if (setterValue != null)
                listOf(baseResolveProviderService.baseKotlinConverter.convertOrEmpty(setterValue, this))
            else
                emptyList()
        }

        override fun getArgumentForParameter(i: Int): UExpression? = valueArguments.getOrNull(i)

        override val typeArgumentCount: Int
            get() = 0

        override val typeArguments: List<PsiType>
            get() = emptyList()

        override val returnType: PsiType?
            get() = resolvedMethod.returnType

        override val kind: UastCallKind
            get() = UastCallKind.METHOD_CALL

        override fun resolve(): PsiMethod = resolvedMethod

        override fun equals(other: Any?): Boolean {
            if (other !is KotlinAccessorCallExpression) {
                return false
            }
            if (this.sourcePsi != other.sourcePsi) {
                return false
            }
            return this.setterValue == other.setterValue
        }

        override fun hashCode(): Int {
            // NB: sourcePsi is shared with the parent reference expression, so using super.hashCode from abstract expression,
            // which uses the same sourcePsi, will result in a hash collision.
            return sourcePsi.hashCode() * 31 + (setterValue?.hashCode() ?: 0)
        }
    }

}
