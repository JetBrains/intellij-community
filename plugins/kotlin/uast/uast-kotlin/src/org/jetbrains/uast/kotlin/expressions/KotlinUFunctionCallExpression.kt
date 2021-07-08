// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin

import com.intellij.psi.*
import com.intellij.psi.util.PsiTypesUtil
import org.jetbrains.kotlin.descriptors.ConstructorDescriptor
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.resolve.CompileTimeConstantUtils
import org.jetbrains.kotlin.resolve.calls.callUtil.getResolvedCall
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall
import org.jetbrains.uast.*
import org.jetbrains.uast.internal.acceptList
import org.jetbrains.uast.kotlin.internal.TypedResolveResult
import org.jetbrains.uast.visitor.UastVisitor

class KotlinUFunctionCallExpression(
        override val sourcePsi: KtCallElement,
        givenParent: UElement?,
        private val _resolvedCall: ResolvedCall<*>?
) : KotlinAbstractUExpression(givenParent), UCallExpressionEx, KotlinUElementWithType, UMultiResolvable {

    constructor(psi: KtCallElement, uastParent: UElement?) : this(psi, uastParent, null)

    private val resolvedCall
        get() = _resolvedCall ?: sourcePsi.getResolvedCall(sourcePsi.analyze())

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
                    ?: calleeExpression, this)
        }
    }

    override val valueArgumentCount: Int
        get() = sourcePsi.valueArguments.size

    override val valueArguments by lz { sourcePsi.valueArguments.map { KotlinConverter.convertOrEmpty(it.getArgumentExpression(), this) } }

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

    override val typeArguments by lz { sourcePsi.typeArguments.map { it.typeReference.toPsiType(this, boxed = true) } }

    override val returnType: PsiType?
        get() = getExpressionType()

    override val kind: UastCallKind by lz {
        val resolvedCall = resolvedCall ?: return@lz UastCallKind.METHOD_CALL
        when {
            resolvedCall.resultingDescriptor is ConstructorDescriptor -> UastCallKind.CONSTRUCTOR_CALL
            this.isAnnotationArgumentArrayInitializer() -> UastCallKind.NESTED_ARRAY_INITIALIZER
            else -> UastCallKind.METHOD_CALL
        }
    }

    override val receiver: UExpression?
        get() {
            (uastParent as? UQualifiedReferenceExpression)?.let {
                if (it.selector == this) return it.receiver
            }

            val ktNameReferenceExpression = sourcePsi.calleeExpression as? KtNameReferenceExpression ?: return null
            val localCallableDeclaration =
                baseResolveProviderService.resolveToDeclaration(ktNameReferenceExpression) as? PsiVariable ?: return null
            if (localCallableDeclaration !is PsiLocalVariable && localCallableDeclaration !is PsiParameter) return null

            // an implicit receiver for variables calls (KT-25524)
            return object : KotlinAbstractUExpression(this), UReferenceExpression {

                override val sourcePsi: KtNameReferenceExpression get() = ktNameReferenceExpression

                override val resolvedName: String? get() = localCallableDeclaration.name

                override fun resolve(): PsiElement? = localCallableDeclaration

            }

        }

    private val multiResolved by lazy(fun(): Iterable<TypedResolveResult<PsiMethod>> {
        val contextElement = sourcePsi
        val calleeExpression = contextElement.calleeExpression as? KtReferenceExpression ?: return emptyList()
        val methodName = methodName ?: calleeExpression.text ?: return emptyList()
        val variants = baseResolveProviderService.getReferenceVariants(calleeExpression, methodName)
        return variants.flatMap {
            when (it) {
                is PsiClass -> it.constructors.asSequence()
                is PsiMethod -> sequenceOf(it)
                else -> emptySequence()
            }
        }.map { TypedResolveResult(it) }.asIterable()
    })

    override fun multiResolve(): Iterable<TypedResolveResult<PsiMethod>> = multiResolved


    override fun resolve(): PsiMethod? {
        val descriptor = resolvedCall?.resultingDescriptor ?: return null
        return resolveToPsiMethod(sourcePsi, descriptor)
    }

    override fun accept(visitor: UastVisitor) {
        if (visitor.visitCallExpression(this)) return
        uAnnotations.acceptList(visitor)
        methodIdentifier?.accept(visitor)
        classReference.accept(visitor)
        valueArguments.acceptList(visitor)

        visitor.afterVisitCallExpression(this)
    }

    private fun isAnnotationArgumentArrayInitializer(): Boolean {
        // KtAnnotationEntry (or KtCallExpression when annotation is nested) -> KtValueArgumentList -> KtValueArgument -> arrayOf call
        val isAnnotationArgument = when (val elementAt2 = sourcePsi.parents.elementAtOrNull(2)) {
            is KtAnnotationEntry -> true
            is KtCallExpression -> elementAt2.getParentOfType<KtAnnotationEntry>(true, KtDeclaration::class.java) != null
            else -> false
        }
        if (!isAnnotationArgument) return false

        val resolvedCall = resolvedCall ?: return false
        return CompileTimeConstantUtils.isArrayFunctionCall(resolvedCall)
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
