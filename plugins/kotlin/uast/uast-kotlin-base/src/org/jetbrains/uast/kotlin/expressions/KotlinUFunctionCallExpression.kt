// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin

import com.intellij.psi.*
import com.intellij.psi.util.PropertyUtilBase
import com.intellij.psi.util.PsiTypesUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.name.SpecialNames
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getCallNameExpression
import org.jetbrains.kotlin.util.OperatorNameConventions
import org.jetbrains.uast.*
import org.jetbrains.uast.internal.acceptList
import org.jetbrains.uast.kotlin.internal.TypedResolveResult
import org.jetbrains.uast.visitor.UastVisitor

@ApiStatus.Internal
class KotlinUFunctionCallExpression(
    override val sourcePsi: KtCallElement,
    givenParent: UElement?,
) : KotlinAbstractUExpression(givenParent), UCallExpression, KotlinUElementWithType, UMultiResolvable {

    override val receiverType by lz {
        baseResolveProviderService.getReceiverType(sourcePsi, this)
    }

    override val methodName by lz {
        baseResolveProviderService.resolvedFunctionName(sourcePsi)
    }

    override val classReference: UReferenceExpression by lz {
        KotlinClassViaConstructorUSimpleReferenceExpression(sourcePsi, methodName.orAnonymous("class"), this)
    }

    override val methodIdentifier: UIdentifier? by lz {
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
        val resolvedCall = baseResolveProviderService.resolveCall(sourcePsi)
        if (resolvedCall != null) {
            val actualParamIndex = if (baseResolveProviderService.isResolvedToExtension(sourcePsi)) i - 1 else i
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
            ktTypeProjection.typeReference?.let { baseResolveProviderService.resolveToType(it, this, isBoxed = true) } ?: UastErrorType
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

        val callee = sourcePsi.calleeExpression

        if (callee is KtLambdaExpression && methodName == OperatorNameConventions.INVOKE.identifier) {
            baseResolveProviderService.baseKotlinConverter.convertOrNull(callee, uastParent)?.let { return@lz it }
        }

        val ktNameReferenceExpression = callee as? KtNameReferenceExpression ?: return@lz null
        val callableDeclaration = baseResolveProviderService.resolveToDeclaration(ktNameReferenceExpression) ?: return@lz null

        val variable = when (callableDeclaration) {
            is PsiVariable -> callableDeclaration
            is PsiMethod -> {
                callableDeclaration.containingClass?.let { containingClass ->
                    PropertyUtilBase.getPropertyName(callableDeclaration.name)?.let { propertyName ->
                        PropertyUtilBase.findPropertyField(containingClass, propertyName, true)
                    }
                }
            }
            else -> null
        } ?: return@lz null

        // an implicit receiver for variables calls (KT-25524)
        object : KotlinAbstractUExpression(this), UReferenceExpression {

            override val sourcePsi: KtNameReferenceExpression get() = ktNameReferenceExpression

            override val resolvedName: String? get() = variable.name

            override fun resolve(): PsiElement = variable

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

    override fun isMethodNameOneOf(names: Collection<String>): Boolean {
        if (methodNameCanBeOneOf(names)) {
            // canMethodNameBeOneOf can return false-positive results, additional resolve is needed
            val methodName = methodName ?: return false
            return methodName in names
        }
        return false
    }

    fun methodNameCanBeOneOf(names: Collection<String>): Boolean {
        if (isMethodNameOneOfWithoutConsideringImportAliases(names)) return true
        val ktFile = sourcePsi.containingKtFile
        val aliasedNames = collectAliasedNamesForName(ktFile, names)
        return isMethodNameOneOfWithoutConsideringImportAliases(aliasedNames)
    }

    /**
     * For the [actualNames], returns the possible import alias name it might be expanded to
     *
     * E.g., for the file with imports
     * ```
     * import a.b.c as foo
     * ```
     * The call `collectAliasedNamesForName(ktFile, listOf("c")` will return `["foo"]`
     */
    private fun collectAliasedNamesForName(ktFile: KtFile, actualNames: Collection<String>): Set<String> =
        buildSet {
            for (importDirective in ktFile.importDirectives) {
                val importedName = importDirective.importedFqName?.pathSegments()?.lastOrNull()?.asString()
                if (importedName in actualNames) {
                    importDirective.aliasName?.let(::add)
                }
            }
        }


    private fun isMethodNameOneOfWithoutConsideringImportAliases(names: Collection<String>): Boolean {
        if (names.isEmpty()) return false
        if (names.any { it in methodNamesForWhichResolveIsNeeded }) {
            // we need an additional resolve to say if the method name is one of expected
            return true
        }
        val referencedName = sourcePsi.getCallNameExpression()?.getReferencedName() ?: return false
        return referencedName in names
    }

    companion object {
        private val methodNamesForWhichResolveIsNeeded = buildSet {
            /*
                operator fun Int.invoke() {}
                val foo = 1
                foo() // the methodName is `invoke` here which we cannot determine by psi, the resolve is needed
             */
            add(OperatorNameConventions.INVOKE.asString())

            /*
              class A
              A() // the methodName is `<init>` here which we cannot determine by psi, the resolve is needed
           */
            add(SpecialNames.INIT.asString())
        }
    }
}
