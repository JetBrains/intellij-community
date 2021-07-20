// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin

import com.intellij.psi.*
import com.intellij.psi.util.PsiTypesUtil
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.calls.callUtil.getResolvedCall
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall
import org.jetbrains.uast.*
import org.jetbrains.uast.internal.acceptList
import org.jetbrains.uast.kotlin.internal.TypedResolveResult
import org.jetbrains.uast.visitor.UastVisitor

class KotlinUFunctionCallExpression(
    override val sourcePsi: KtCallElement,
    givenParent: UElement?,
) : KotlinAbstractUExpression(givenParent), UCallExpression, KotlinUElementWithType, UMultiResolvable {

    private val resolvedCall: ResolvedCall<*>?
        get() = sourcePsi.getResolvedCall(sourcePsi.analyze())

    override val receiverType by lz {
        val resolvedCall = this.resolvedCall ?: return@lz null
        val receiver = resolvedCall.dispatchReceiver ?: resolvedCall.extensionReceiver ?: return@lz null
        receiver.type.toPsiType(this, sourcePsi, boxed = true)
    }

    override val methodName by lz { resolvedCall?.resultingDescriptor?.name?.asString() }

    override val classReference by lz {
        KotlinClassViaConstructorUSimpleReferenceExpression(sourcePsi, methodName.orAnonymous("class"), this)
    }

    override val methodIdentifier by lz {
        if (sourcePsi is KtSuperTypeCallEntry) {
            ((sourcePsi.parent as? KtInitializerList)?.parent as? KtEnumEntry)?.let { ktEnumEntry ->
                return@lz KotlinUIdentifier(ktEnumEntry.nameIdentifier, this)
            }
        }

        when (val calleeExpression = sourcePsi.calleeExpression) {
            null -> null
            is KtNameReferenceExpression ->
                KotlinUIdentifier(calleeExpression.getReferencedNameElement(), this)
            is KtConstructorDelegationReferenceExpression ->
                KotlinUIdentifier(calleeExpression.firstChild ?: calleeExpression, this)
            is KtConstructorCalleeExpression -> {
                val referencedNameElement = calleeExpression.constructorReferenceExpression?.getReferencedNameElement()
                if (referencedNameElement != null) KotlinUIdentifier(referencedNameElement, this)
                else generateSequence<PsiElement>(calleeExpression) { it.firstChild?.takeIf { it.nextSibling == null } }
                    .lastOrNull()
                    ?.takeIf { it.firstChild == null }
                    ?.let { KotlinUIdentifier(it, this) }
            }
            is KtLambdaExpression ->
                KotlinUIdentifier(calleeExpression.functionLiteral.lBrace, this)
            else -> KotlinUIdentifier(
                sourcePsi.valueArgumentList?.leftParenthesis
                    ?: sourcePsi.lambdaArguments.singleOrNull()?.getLambdaExpression()?.functionLiteral?.lBrace
                    ?: sourcePsi.typeArgumentList?.firstChild
                    ?: calleeExpression, this)
        }
    }

    override val valueArgumentCount: Int
        get() = sourcePsi.valueArguments.size

    override val valueArguments by lz {
        sourcePsi.valueArguments.map {
            baseResolveProviderService.baseKotlinConverter.convertOrEmpty(it.getArgumentExpression(), this)
        }
    }

    override fun getArgumentForParameter(i: Int): UExpression? {
        val resolvedCall = resolvedCall
        if (resolvedCall != null) {
            val actualParamIndex = if (resolvedCall.extensionReceiver == null) i else i - 1
            if (actualParamIndex == -1) return receiver
            return baseResolveProviderService.getArgumentForParameter(sourcePsi, actualParamIndex, this)
        }
        val argument = valueArguments.getOrNull(i) ?: return null
        val argumentType = argument.getExpressionType()
        for (resolveResult in multiResolve()) {
            val psiMethod = resolveResult.element as? PsiMethod ?: continue
            val psiParameter = psiMethod.parameterList.parameters.getOrNull(i) ?: continue

            if (argumentType == null || psiParameter.type.isAssignableFrom(argumentType))
                return argument
        }
        return null
    }

    override fun getExpressionType(): PsiType? {
        super<KotlinUElementWithType>.getExpressionType()?.let { return it }
        for (resolveResult in multiResolve()) {
            val psiMethod = resolveResult.element
            when {
                psiMethod.isConstructor ->
                    psiMethod.containingClass?.let { return PsiTypesUtil.getClassType(it) }
                else ->
                    psiMethod.returnType?.let { return it }
            }
        }
        return null
    }

    override val typeArgumentCount: Int
        get() = sourcePsi.typeArguments.size

    override val typeArguments by lz {
        sourcePsi.typeArguments.map { ktTypeProjection ->
            ktTypeProjection.typeReference?.let { baseResolveProviderService.resolveToType(it, this) } ?: UastErrorType
        }
    }

    override val returnType: PsiType? by lz {
        getExpressionType()
    }

    override val kind: UastCallKind by lz {
        baseResolveProviderService.callKind(sourcePsi)
    }

    override val receiver: UExpression? by lz {
        (uastParent as? UQualifiedReferenceExpression)?.let {
            if (it.selector == this) return@lz it.receiver
        }

        val ktNameReferenceExpression = sourcePsi.calleeExpression as? KtNameReferenceExpression ?: return@lz null
        val localCallableDeclaration =
            baseResolveProviderService.resolveToDeclaration(ktNameReferenceExpression) as? PsiVariable ?: return@lz null
        if (localCallableDeclaration !is PsiLocalVariable && localCallableDeclaration !is PsiParameter) return@lz null

        // an implicit receiver for variables calls (KT-25524)
        object : KotlinAbstractUExpression(this), UReferenceExpression {

            override val sourcePsi: KtNameReferenceExpression get() = ktNameReferenceExpression

            override val resolvedName: String? get() = localCallableDeclaration.name

            override fun resolve(): PsiElement = localCallableDeclaration

        }
    }

    private val multiResolved: Iterable<TypedResolveResult<PsiMethod>> by lz {
        val contextElement = sourcePsi
        val calleeExpression = contextElement.calleeExpression as? KtReferenceExpression ?: return@lz emptyList()
        val methodName = methodName ?: calleeExpression.text ?: return@lz emptyList()
        val variants = baseResolveProviderService.getReferenceVariants(calleeExpression, methodName)
        variants.flatMap {
            when (it) {
                is PsiClass -> it.constructors.asSequence()
                is PsiMethod -> sequenceOf(it)
                else -> emptySequence()
            }
        }.map { TypedResolveResult(it) }.asIterable()
    }

    override fun multiResolve(): Iterable<TypedResolveResult<PsiMethod>> =
        multiResolved

    override fun resolve(): PsiMethod? =
        baseResolveProviderService.resolveCall(sourcePsi)

    override fun accept(visitor: UastVisitor) {
        if (visitor.visitCallExpression(this)) return
        uAnnotations.acceptList(visitor)
        methodIdentifier?.accept(visitor)
        classReference.accept(visitor)
        valueArguments.acceptList(visitor)

        visitor.afterVisitCallExpression(this)
    }

    override fun convertParent(): UElement? = super.convertParent().let { result ->
        when (result) {
            is UMethod -> result.uastBody ?: result
            is UClass ->
                result.methods
                    .filterIsInstance<KotlinConstructorUMethod>()
                    .firstOrNull { it.isPrimary }
                    ?.uastBody
                ?: result
            else -> result
        }
    }
}
