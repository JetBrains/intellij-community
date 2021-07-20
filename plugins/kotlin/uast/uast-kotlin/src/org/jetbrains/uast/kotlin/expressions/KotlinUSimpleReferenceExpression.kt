// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin

import com.intellij.openapi.util.Key
import com.intellij.psi.*
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.calls.callUtil.getResolvedCall
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall
import org.jetbrains.kotlin.synthetic.SyntheticJavaPropertyDescriptor
import org.jetbrains.uast.*
import org.jetbrains.uast.internal.acceptList
import org.jetbrains.uast.kotlin.internal.DelegatedMultiResolve
import org.jetbrains.uast.visitor.UastVisitor

var PsiElement.destructuringDeclarationInitializer: Boolean? by UserDataProperty(Key.create("kotlin.uast.destructuringDeclarationInitializer"))

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
        val bindingContext = sourcePsi.analyze()
        val access = sourcePsi.readWriteAccess()
        val resolvedCall = sourcePsi.getResolvedCall(bindingContext)
        val resultingDescriptor = resolvedCall?.resultingDescriptor as? SyntheticJavaPropertyDescriptor
        if (resultingDescriptor != null) {
            val setterValue = if (access.isWrite) {
                findAssignment(sourcePsi, sourcePsi.parent)?.right ?: run {
                    visitor.afterVisitSimpleNameReferenceExpression(this)
                    return
                }
            } else {
                null
            }

            if (access.isRead) {
                val getDescriptor = resultingDescriptor.getMethod
                KotlinAccessorCallExpression(sourcePsi, this, resolvedCall, getDescriptor, null).accept(visitor)
            }

            if (access.isWrite && setterValue != null) {
                val setDescriptor = resultingDescriptor.setMethod
                if (setDescriptor != null) {
                    KotlinAccessorCallExpression(sourcePsi, this, resolvedCall, setDescriptor, setterValue).accept(visitor)
                }
            }
        }
    }

    private tailrec fun findAssignment(prev: PsiElement?, element: PsiElement?): KtBinaryExpression? = when (element) {
        is KtBinaryExpression -> if (element.left == prev && element.operationToken == KtTokens.EQ) element else null
        is KtQualifiedExpression -> findAssignment(element, element.parent)
        is KtSimpleNameExpression -> findAssignment(element, element.parent)
        else -> null
    }

    class KotlinAccessorCallExpression(
        override val sourcePsi: KtSimpleNameExpression,
        override val uastParent: KotlinUSimpleReferenceExpression,
        private val resolvedCall: ResolvedCall<*>,
        private val accessorDescriptor: DeclarationDescriptor,
        val setterValue: KtExpression?
    ) : UCallExpressionEx, DelegatedMultiResolve {
        override val methodName: String?
            get() = accessorDescriptor.name.asString()

        override val receiver: UExpression?
            get() {
                val containingElement = uastParent.uastParent
                return if (containingElement is UQualifiedReferenceExpression && containingElement.selector == this)
                    containingElement.receiver
                else
                    null
            }

        override val javaPsi: PsiElement? get() = null
        override val psi: PsiElement? get() = sourcePsi

        override val uAnnotations: List<UAnnotation>
            get() = emptyList()

        override val receiverType by lz {
            val type = (resolvedCall.dispatchReceiver ?: resolvedCall.extensionReceiver)?.type ?: return@lz null
            type.toPsiType(this, sourcePsi, boxed = true)
        }

        override val methodIdentifier: UIdentifier? by lazy { KotlinUIdentifier(sourcePsi.getReferencedNameElement(), this) }

        override val classReference: UReferenceExpression?
            get() = null

        override val valueArgumentCount: Int
            get() = if (setterValue != null) 1 else 0

        override val valueArguments by lz {
            if (setterValue != null)
                listOf(KotlinConverter.convertOrEmpty(setterValue, this))
            else
                emptyList()
        }

        override fun getArgumentForParameter(i: Int): UExpression? = valueArguments.getOrNull(i)

        override val typeArgumentCount: Int
            get() = resolvedCall.typeArguments.size

        override val typeArguments by lz {
            resolvedCall.typeArguments.values.map { it.toPsiType(this, sourcePsi, true) }
        }

        override val returnType by lz {
            (accessorDescriptor as? CallableDescriptor)?.returnType?.toPsiType(this, sourcePsi, boxed = false)
        }

        override val kind: UastCallKind
            get() = UastCallKind.METHOD_CALL

        override fun resolve(): PsiMethod? = resolveToPsiMethod(sourcePsi, accessorDescriptor)
    }

}
