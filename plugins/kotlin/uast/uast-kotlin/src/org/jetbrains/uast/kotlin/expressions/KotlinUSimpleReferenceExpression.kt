// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin

import com.intellij.psi.*
import org.jetbrains.kotlin.idea.references.readWriteAccess
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.findAssignment
import org.jetbrains.kotlin.resolve.calls.callUtil.getResolvedCall
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall
import org.jetbrains.uast.*
import org.jetbrains.uast.internal.acceptList
import org.jetbrains.uast.kotlin.internal.DelegatedMultiResolve
import org.jetbrains.uast.visitor.UastVisitor

class KotlinUSimpleReferenceExpression(
    override val sourcePsi: KtSimpleNameExpression,
    givenParent: UElement?
) : KotlinAbstractUSimpleReferenceExpression(sourcePsi, givenParent) {

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
        val resolvedMethod = baseResolveProviderService.resolveAccessorCall(sourcePsi) ?: return
        val bindingContext = sourcePsi.analyze()
        val access = sourcePsi.readWriteAccess(useResolveForReadWrite = false)
        val resolvedCall = sourcePsi.getResolvedCall(bindingContext) ?: return
        val setterValue = if (access.isWrite) {
            findAssignment(sourcePsi)?.right ?: run {
                visitor.afterVisitSimpleNameReferenceExpression(this)
                return
            }
        } else {
            null
        }

        if (access.isRead) {
            KotlinAccessorCallExpression(sourcePsi, this, resolvedMethod, resolvedCall, null).accept(visitor)
        }

        if (access.isWrite && setterValue != null) {
            KotlinAccessorCallExpression(sourcePsi, this, resolvedMethod, resolvedCall, setterValue).accept(visitor)
        }
    }

    class KotlinAccessorCallExpression(
        override val sourcePsi: KtSimpleNameExpression,
        givenParent: KotlinUSimpleReferenceExpression,
        private val resolvedMethod: PsiMethod,
        private val resolvedCall: ResolvedCall<*>,
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
            val type = (resolvedCall.dispatchReceiver ?: resolvedCall.extensionReceiver)?.type ?: return@lz null
            type.toPsiType(this, sourcePsi, boxed = true)
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
            get() = resolvedCall.typeArguments.size

        override val typeArguments by lz {
            resolvedCall.typeArguments.values.map { it.toPsiType(this, sourcePsi, true) }
        }

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
