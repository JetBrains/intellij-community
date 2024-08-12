// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.uast.kotlin

import com.intellij.lang.jvm.JvmModifier
import com.intellij.psi.*
import com.intellij.psi.impl.source.PsiClassReferenceType
import com.intellij.psi.util.PropertyUtilBase
import com.intellij.psi.util.PsiTypesUtil
import com.intellij.psi.util.PsiUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.name.SpecialNames
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getCallNameExpression
import org.jetbrains.kotlin.util.OperatorNameConventions
import org.jetbrains.uast.*
import org.jetbrains.uast.kotlin.internal.TypedResolveResult

@ApiStatus.Internal
class KotlinUFunctionCallExpression(
    override val sourcePsi: KtCallElement,
    givenParent: UElement?,
) : KotlinAbstractUExpression(givenParent), UCallExpression, KotlinUElementWithType, UMultiResolvable {

    private var receiverTypePart: Any? = UNINITIALIZED_UAST_PART
    private var methodNamePart: Any? = UNINITIALIZED_UAST_PART
    private var classReferencePart: Any? = UNINITIALIZED_UAST_PART
    private var methodIdentifierPart: Any? = UNINITIALIZED_UAST_PART
    private var returnTypePart: Any? = UNINITIALIZED_UAST_PART
    private var receiverPart: Any? = UNINITIALIZED_UAST_PART

    private var valueArgumentsPart: List<UExpression>? = null
    private var typeArgumentsPart: List<PsiType>? = null

    private var kindValue: UastCallKind? = null
    private var multiResolveTargets: Iterable<TypedResolveResult<PsiMethod>>? = null

    override val receiverType: PsiType?
        get() {
            if (receiverTypePart == UNINITIALIZED_UAST_PART) {
                receiverTypePart = baseResolveProviderService.getReceiverType(sourcePsi, this)
            }
            return receiverTypePart as PsiType?
        }

    override val methodName: String?
        get() {
            if (methodNamePart == UNINITIALIZED_UAST_PART) {
                methodNamePart = baseResolveProviderService.resolvedFunctionName(sourcePsi)
            }
            return methodNamePart as String?
        }

    override val classReference: UReferenceExpression?
        get() {
            if (classReferencePart == UNINITIALIZED_UAST_PART) {
                val resolvedClass = baseResolveProviderService.resolveToClassIfConstructorCall(sourcePsi, this)
                classReferencePart = if (resolvedClass != null) {
                    KotlinClassViaConstructorUSimpleReferenceExpression(
                        sourcePsi.calleeExpression,
                        resolvedClass.name.orAnonymous("class"),
                        resolvedClass,
                        this
                    )
                } else null
            }
            return classReferencePart as UReferenceExpression?
        }

    override val methodIdentifier: UIdentifier?
        get() {
            if (methodIdentifierPart == UNINITIALIZED_UAST_PART) {
                methodIdentifierPart = buildMethodIdentifier()
            }

            return methodIdentifierPart as UIdentifier?
        }

    private fun buildMethodIdentifier(): KotlinUIdentifier? {
        if (sourcePsi is KtSuperTypeCallEntry) {
            ((sourcePsi.parent as? KtInitializerList)?.parent as? KtEnumEntry)?.let { ktEnumEntry ->
                return KotlinUIdentifier(ktEnumEntry.nameIdentifier, this)
            }
        }

        return when (val calleeExpression = sourcePsi.calleeExpression) {
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
                    ?: calleeExpression, this
            )
        }
    }

    override val valueArgumentCount: Int
        get() = sourcePsi.valueArguments.size

    override val valueArguments: List<UExpression>
        get() {
            if (valueArgumentsPart == null) {
                val service = baseResolveProviderService
                valueArgumentsPart = sourcePsi.valueArguments
                    .map { service.baseKotlinConverter.convertOrEmpty(it.getArgumentExpression(), this) }
            }
            return valueArgumentsPart!!
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
        // KTIJ-17870: One-off handling for instantiation of local classes
        if (classReference != null) {
            // [classReference] is created only if this call expression is resolved to constructor.
            // Its [resolve] will return the stored [PsiClass], hence no cost at all.
            val resolvedClass = classReference!!.resolve() as PsiClass
            if (
                // local/anonymous class from Java source
                PsiUtil.isLocalClass(resolvedClass) ||
                // everything else
                resolvedClass.isLocal()
            ) {
                val referenceType = super<KotlinUElementWithType>.getExpressionType()
                when (referenceType) {
                    is PsiClassReferenceType -> {
                        // K2: returns [PsiClassReferenceType], which is correct yet not good for type resolution.
                        // Instead, we create a new instance of [PsiClassReferenceType]
                        // whose [resolve] is just overridden to return `resolvedClass`.
                        return object : PsiClassReferenceType(referenceType.reference, referenceType.languageLevel) {
                            override fun resolve(): PsiClass? {
                                return resolvedClass
                            }
                        }
                    }
                    else -> {
                        // K1: intentionally drop the type conversion of local/anonymous class (KT-15483)
                        // This will be a thin wrapper, [PsiImmediateClassType]
                        // whose [resolve] is already overridden to return the given `resolvedClass`.
                        return PsiTypesUtil.getClassType(resolvedClass)
                    }
                }
            }
        }
        // Regular [getExpressionType] that goes through resolve service.
        super<KotlinUElementWithType>.getExpressionType()?.let { return it }
        // One more chance: multi-resolution
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

    override val typeArguments: List<PsiType>
        get() {
            if (typeArgumentsPart == null) {
                val service = baseResolveProviderService
                typeArgumentsPart = sourcePsi.typeArguments.map { ktTypeProjection ->
                    ktTypeProjection.typeReference?.let { service.resolveToType(it, this, isBoxed = true) }
                        ?: UastErrorType
                }
            }
            return typeArgumentsPart!!
        }

    override val returnType: PsiType?
        get() {
            if (returnTypePart == UNINITIALIZED_UAST_PART) {
                returnTypePart = getExpressionType()
            }
            return returnTypePart as PsiType?
        }

    override val kind: UastCallKind
        get() {
            if (kindValue == null) {
                kindValue = baseResolveProviderService.callKind(sourcePsi)
            }
            return kindValue!!
        }

    override fun hasKind(expectedKind: UastCallKind): Boolean {
        if (expectedKind == UastCallKind.NESTED_ARRAY_INITIALIZER
            && !sourcePsi.isAnnotationArgument
        ) {
            // do not try to resolve arbitrary calls if we only need array initializer inside annotations
            return false
        }

        return super.hasKind(expectedKind)
    }

    override val receiver: UExpression?
        get() {
            if (receiverPart == UNINITIALIZED_UAST_PART) {
                receiverPart = buildReceiver()
            }
            return receiverPart as UExpression?
        }

    private fun buildReceiver(): UExpression? {
        (uastParent as? UQualifiedReferenceExpression)?.let {
            if (it.selector == this) return it.receiver
        }

        val callee = sourcePsi.calleeExpression

        if (callee is KtLambdaExpression && methodName == OperatorNameConventions.INVOKE.identifier) {
            baseResolveProviderService.baseKotlinConverter.convertOrNull(callee, uastParent)?.let { return it }
        }

        val ktNameReferenceExpression = callee as? KtNameReferenceExpression ?: return null
        val callableDeclaration = baseResolveProviderService.resolveToDeclaration(ktNameReferenceExpression) ?: return null

        val variable = when (callableDeclaration) {
            is PsiVariable -> callableDeclaration
            is PsiMethod -> {
                val isStatic = callableDeclaration.hasModifier(JvmModifier.STATIC)
                callableDeclaration.containingClass?.let { containingClass ->
                    PropertyUtilBase.getPropertyName(callableDeclaration.name)?.let { propertyName ->
                        PropertyUtilBase.findPropertyField(containingClass, propertyName, isStatic)
                    }
                }
            }

            else -> null
        } ?: return null

        // an implicit receiver for variables calls (KT-25524)
        return object : KotlinAbstractUExpression(this), UReferenceExpression {
            override val sourcePsi: KtNameReferenceExpression get() = ktNameReferenceExpression

            override val resolvedName: String? get() = variable.name

            override fun resolve(): PsiElement = variable
        }
    }

    private fun getMultiResolved(): Iterable<TypedResolveResult<PsiMethod>> {
        val contextElement = sourcePsi
        val calleeExpression = contextElement.calleeExpression as? KtReferenceExpression ?: return emptyList()
        val methodName = methodName ?: calleeExpression.text ?: return emptyList()
        val variants = baseResolveProviderService.getReferenceVariants(calleeExpression, methodName)

        return variants
            .flatMap {
                when (it) {
                    is PsiClass -> it.constructors.asSequence()
                    is PsiMethod -> sequenceOf(it)
                    else -> emptySequence()
                }
            }
            .map { TypedResolveResult(it) }
            .asIterable()
    }

    override fun multiResolve(): Iterable<TypedResolveResult<PsiMethod>> {
        if (multiResolveTargets == null) {
            multiResolveTargets = getMultiResolved()
        }
        return multiResolveTargets!!
    }

    override fun resolve(): PsiMethod? =
        baseResolveProviderService.resolveCall(sourcePsi)

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
        if (methodNameCanBeOneOf(sourcePsi, names)) {
            // canMethodNameBeOneOf can return false-positive results, additional resolve is needed
            val methodName = methodName ?: return false
            return methodName in names
        }

        return false
    }

    companion object {
        /**
         * Can return false-positive results, additional resolve is needed
         */
        internal fun methodNameCanBeOneOf(call: KtCallElement, names: Collection<String>): Boolean {
            if (names.isEmpty()) return false
            if (names.any { it in methodNamesForWhichResolveIsNeeded }) {
                // we need an additional resolve to say if the method name is one of expected
                return true
            }

            val referencedName = call.getCallNameExpression()?.getReferencedName() ?: return false
            if (referencedName in names) return true

            val ktFile = call.containingKtFile
            if (!ktFile.hasImportAlias()) return false

            for (directive in ktFile.importDirectives) {
                val aliasName = directive.aliasName ?: continue
                if (referencedName != aliasName) continue

                val importedName = directive.importedFqName?.shortName()?.asString() ?: continue
                if (importedName in names) return true
            }

            return false
        }

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
