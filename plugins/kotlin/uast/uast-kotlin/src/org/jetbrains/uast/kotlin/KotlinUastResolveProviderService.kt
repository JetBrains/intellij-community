// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin

import com.intellij.psi.*
import org.jetbrains.kotlin.analysis.api.types.KtTypeNullability
import org.jetbrains.kotlin.asJava.toLightAnnotation
import org.jetbrains.kotlin.builtins.createFunctionType
import org.jetbrains.kotlin.codegen.state.KotlinTypeMapper
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.idea.util.actionUnderSafeAnalyzeBlock
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.FqNameUnsafe
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.resolve.calls.components.isVararg
import org.jetbrains.kotlin.resolve.calls.model.ArgumentMatch
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.kotlin.resolve.calls.util.getType
import org.jetbrains.kotlin.resolve.constants.UnsignedErrorValueTypeConstant
import org.jetbrains.kotlin.resolve.descriptorUtil.annotationClass
import org.jetbrains.kotlin.resolve.descriptorUtil.builtIns
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameUnsafe
import org.jetbrains.kotlin.resolve.lazy.ForceResolveUtil
import org.jetbrains.kotlin.resolve.sam.SamConstructorDescriptor
import org.jetbrains.kotlin.resolve.source.getPsi
import org.jetbrains.kotlin.synthetic.SyntheticJavaPropertyDescriptor
import org.jetbrains.kotlin.types.*
import org.jetbrains.kotlin.types.error.ErrorUtils
import org.jetbrains.kotlin.types.typeUtil.TypeNullability
import org.jetbrains.kotlin.types.typeUtil.nullability
import org.jetbrains.uast.*
import org.jetbrains.uast.kotlin.psi.UastKotlinPsiParameterBase

interface KotlinUastResolveProviderService : BaseKotlinUastResolveProviderService {
    @Deprecated(
        "Do not use the old frontend, retroactively named as FE1.0, since K2 with the new frontend is coming.\n" +
                "Please use analysis API: https://github.com/JetBrains/kotlin/blob/master/docs/analysis/analysis-api/analysis-api.md",
        replaceWith = ReplaceWith("analyze(element) { }", "org.jetbrains.kotlin.analysis.api.analyze")
    )
    fun getBindingContext(element: KtElement): BindingContext

    @Deprecated(
        "Do not use the old frontend, retroactively named as FE1.0, since K2 with the new frontend is coming.\n" +
                "Please use analysis API: https://github.com/JetBrains/kotlin/blob/master/docs/analysis/analysis-api/analysis-api.md",
        replaceWith = ReplaceWith("analyze(element) { }", "org.jetbrains.kotlin.analysis.api.analyze")
    )
    fun getBindingContextIfAny(element: KtElement): BindingContext? = getBindingContext(element)

    @Deprecated("For binary compatibility, please, use KotlinUastTypeMapper")
    fun getTypeMapper(element: KtElement): KotlinTypeMapper?

    fun getLanguageVersionSettings(element: KtElement): LanguageVersionSettings

    override val languagePlugin: UastLanguagePlugin
        get() = kotlinUastPlugin

    override val baseKotlinConverter: BaseKotlinConverter
        get() = KotlinConverter

    override fun convertToPsiAnnotation(ktElement: KtElement): PsiAnnotation? {
        return ktElement.actionUnderSafeAnalyzeBlock({ ktElement.toLightAnnotation() }, { null })
    }

    private fun getResolvedCall(sourcePsi: KtCallElement): ResolvedCall<*>? {
        val annotationEntry = sourcePsi.getParentOfType<KtAnnotationEntry>(false) ?: return null
        val bindingContext = sourcePsi.analyze()
        val annotationDescriptor = bindingContext[BindingContext.ANNOTATION, annotationEntry] ?: return null
        ForceResolveUtil.forceResolveAllContents(annotationDescriptor)
        return sourcePsi.getResolvedCall(bindingContext)
    }

    override fun convertValueArguments(ktCallElement: KtCallElement, parent: UElement): List<UNamedExpression>? {
        val resolvedCall = getResolvedCall(ktCallElement) ?: return null
        return resolvedCall.valueArguments.entries.mapNotNull {
            val arguments = it.value.arguments
            val name = it.key.name.asString()
            when {
                arguments.size == 1 ->
                    KotlinUNamedExpression.create(name, arguments.first(), parent)
                arguments.size > 1 ->
                    KotlinUNamedExpression.create(name, arguments, parent)
                else -> null
            }
        }
    }

    override fun findAttributeValueExpression(uAnnotation: KotlinUAnnotation, arg: ValueArgument): UExpression? {
        val annotationEntry = uAnnotation.sourcePsi
        val resolvedCall = annotationEntry.getResolvedCall(annotationEntry.analyze())
        val mapping = resolvedCall?.getArgumentMapping(arg)
        return (mapping as? ArgumentMatch)?.let { match ->
            val namedExpression = uAnnotation.attributeValues.find { it.name == match.valueParameter.name.asString() }
            namedExpression?.expression as? KotlinUVarargExpression ?: namedExpression
        }
    }

    override fun findDefaultValueForAnnotationAttribute(ktCallElement: KtCallElement, name: String): KtExpression? {
        val parameter = ktCallElement.resolveToClassDescriptor()
            ?.unsubstitutedPrimaryConstructor
            ?.valueParameters
            ?.find { it.name.asString() == name } ?: return null

        return (parameter.source.getPsi() as? KtParameter)?.defaultValue
    }

    override fun getArgumentForParameter(ktCallElement: KtCallElement, index: Int, parent: UElement): UExpression? {
        return ktCallElement.getResolvedCall(ktCallElement.analyze())?.let {
            getArgumentExpressionByIndex(index, it, parent)
        }
    }

    private fun getArgumentExpressionByIndex(
        actualParamIndex: Int,
        resolvedCall: ResolvedCall<out CallableDescriptor>,
        parent: UElement,
    ): UExpression? {
        val (parameter, resolvedArgument) = resolvedCall.valueArguments.entries.find { it.key.index == actualParamIndex } ?: return null
        val arguments = resolvedArgument.arguments
        if (arguments.isEmpty()) return null
        if (arguments.size == 1) {
            val argument = arguments.single()
            if (parameter.varargElementType != null && argument.getSpreadElement() == null) {
                return baseKotlinConverter.createVarargsHolder(arguments, parent)
            }
            return baseKotlinConverter.convertOrEmpty(argument.getArgumentExpression(), parent)
        }
        return baseKotlinConverter.createVarargsHolder(arguments, parent)
    }

    override fun getImplicitReturn(ktLambdaExpression: KtLambdaExpression, parent: UElement): KotlinUImplicitReturnExpression? {
        val lastExpression = ktLambdaExpression.bodyExpression?.statements?.lastOrNull() ?: return null
        val context = lastExpression.analyze()
        if (context[BindingContext.USED_AS_RESULT_OF_LAMBDA, lastExpression] == true) {
            return KotlinUImplicitReturnExpression(parent).apply {
                returnExpression = baseKotlinConverter.convertOrEmpty(lastExpression, this)
            }
        }
        return null
    }

    override fun getImplicitParameters(
        ktLambdaExpression: KtLambdaExpression,
        parent: UElement,
        includeExplicitParameters: Boolean,
    ): List<KotlinUParameter> {
        val functionDescriptor =
            ktLambdaExpression.analyze()[BindingContext.FUNCTION, ktLambdaExpression.functionLiteral] ?: return emptyList()

        val parameters = if (includeExplicitParameters) functionDescriptor.explicitParameters else functionDescriptor.valueParameters

        return parameters.map { p ->
            KotlinUParameter(
                UastKotlinPsiParameterBase(
                    name = p.name.asString(),
                    type = p.type.toPsiType(parent, ktLambdaExpression, PsiTypeConversionConfiguration.create(ktLambdaExpression)),
                    parent = ktLambdaExpression,
                    ktOrigin = ktLambdaExpression,
                    language = ktLambdaExpression.language,
                    isVarArgs = p.isVararg,
                    ktDefaultValue = null
                ),
                null,
                parent
            )
        }
    }

    override fun getPsiAnnotations(psiElement: PsiModifierListOwner): Array<PsiAnnotation> {
        return psiElement.actionUnderSafeAnalyzeBlock({ psiElement.annotations }, { emptyArray() })
    }

    override fun getReferenceVariants(ktExpression: KtExpression, nameHint: String): Sequence<PsiElement> {
        val unwrappedPsi = KtPsiUtil.deparenthesize(ktExpression) ?: ktExpression
        return sequence {
            when (unwrappedPsi) {
                is KtUnaryExpression -> {
                    if (unwrappedPsi.operationToken in KtTokens.INCREMENT_AND_DECREMENT ||
                        unwrappedPsi.operationToken == KtTokens.EXCLEXCL
                    ) {
                        // E.g., `i++` -> access to `i`
                        unwrappedPsi.baseExpression?.let { resolveToDeclaration(it) }?.let { yield(it) }
                    }
                    // Look for regular function call, e.g., inc() in `i++`
                    resolveToDeclaration(ktExpression)?.let { yield(it) }
                }
                is KtBinaryExpression -> {
                    val left = unwrappedPsi.left
                    when (unwrappedPsi.operationToken) {
                      KtTokens.EQ -> {
                          if (left is KtArrayAccessExpression) {
                              // E.g., `array[...] = ...` -> access to `array[...]`, i.e., (overloaded) setter
                              val context = left.analyze()
                              val resolvedSetCall = context[BindingContext.INDEXED_LVALUE_SET, left]
                              resolvedSetCall?.resultingDescriptor?.let { resolveToPsiMethod(unwrappedPsi, it) }?.let { yield(it) }
                          } else {
                              // E.g. `i = ...` -> access to `i`
                              left?.let {  resolveToDeclaration(it) }?.let { yield(it) }
                          }
                      }
                      in KtTokens.AUGMENTED_ASSIGNMENTS -> {
                          val context = left?.analyze() ?: BindingContext.EMPTY
                          if (left is KtArrayAccessExpression) {
                              // E.g., `array[...] += ...` -> access to `array[...]`, i.e., (overloaded) getter and setter
                              val resolvedGetCall = context[BindingContext.INDEXED_LVALUE_GET, left]
                              resolvedGetCall?.resultingDescriptor?.let { resolveToPsiMethod(unwrappedPsi, it) }?.let { yield(it) }
                              val resolvedSetCall = context[BindingContext.INDEXED_LVALUE_SET, left]
                              resolvedSetCall?.resultingDescriptor?.let { resolveToPsiMethod(unwrappedPsi, it) }?.let { yield(it) }
                          } else {
                              // Look for regular function call, e.g., plusAssign() in `i += j`
                              resolveToDeclaration(ktExpression)?.let { yield(it) }
                              when (val descriptor = left?.getResolvedCall(context)?.resultingDescriptor) {
                                  is SyntheticJavaPropertyDescriptor -> {
                                      resolveToPsiMethod(ktExpression, descriptor.getMethod)?.let { yield(it) }
                                      descriptor.setMethod?.let { resolveToPsiMethod(ktExpression, it) }?.let { yield(it) }
                                  }
                                  is CallableDescriptor ->
                                      resolveToPsiMethod(ktExpression, descriptor)?.let { yield(it) }
                                  else -> {}
                              }
                          }
                      }
                      else -> {}
                    }
                }
                else -> {
                    // TODO: regular function call resolution?
                }
            }
        }
    }

    override fun resolveBitwiseOperators(ktBinaryExpression: KtBinaryExpression): UastBinaryOperator {
        val other = UastBinaryOperator.OTHER
        val ref = ktBinaryExpression.operationReference
        val resolvedCall = ktBinaryExpression.operationReference.getResolvedCall(ref.analyze()) ?: return other
        val resultingDescriptor = resolvedCall.resultingDescriptor as? FunctionDescriptor ?: return other
        val applicableOperator = KotlinUBinaryExpression.BITWISE_OPERATORS[resultingDescriptor.name.asString()] ?: return other

        val containingClass = resultingDescriptor.containingDeclaration as? ClassDescriptor ?: return other
        return if (containingClass.typeConstructor.supertypes.any {
                it.constructor.declarationDescriptor?.fqNameSafe?.asString() == "kotlin.Number"
            }) applicableOperator else other
    }

    override fun resolveCall(ktElement: KtElement): PsiMethod? {
        return resolveToPsiMethod(ktElement)
    }

    override fun resolveSyntheticJavaPropertyAccessorCall(ktSimpleNameExpression: KtSimpleNameExpression): PsiMethod? {
        val resolvedCall = ktSimpleNameExpression.getResolvedCall(ktSimpleNameExpression.analyze()) ?: return null
        val resultingDescriptor = resolvedCall.resultingDescriptor as? SyntheticJavaPropertyDescriptor ?: return null
        val access = ktSimpleNameExpression.readWriteAccess()
        val descriptor = (if (access.isWrite) resultingDescriptor.setMethod else resultingDescriptor.getMethod) ?: return null
        return resolveToPsiMethod(ktSimpleNameExpression, descriptor)
    }

    override fun isResolvedToExtension(ktCallElement: KtCallElement): Boolean {
        val resolvedCall = ktCallElement.getResolvedCall(ktCallElement.analyze()) ?: return false
        return resolvedCall.extensionReceiver != null
    }

    override fun resolvedFunctionName(ktCallElement: KtCallElement): String? {
        val resolvedCall = ktCallElement.getResolvedCall(ktCallElement.analyze()) ?: return null
        return resolvedCall.resultingDescriptor.name.asString()
    }

    override fun qualifiedAnnotationName(ktCallElement: KtCallElement): String? {
        return ktCallElement.resolveToClassDescriptor().takeUnless(ErrorUtils::isError)
            ?.fqNameUnsafe
            ?.takeIf(FqNameUnsafe::isSafe)
            ?.toSafe()
            ?.toString()
    }

    override fun callKind(ktCallElement: KtCallElement): UastCallKind {
        val resolvedCall = ktCallElement.getResolvedCall(ktCallElement.analyze()) ?: return UastCallKind.METHOD_CALL
        val fqName = DescriptorUtils.getFqNameSafe(resolvedCall.candidateDescriptor)
        return when {
            resolvedCall.resultingDescriptor is SamConstructorDescriptor ||
            resolvedCall.resultingDescriptor.original is SamConstructorDescriptor ||
            resolvedCall.resultingDescriptor is ConstructorDescriptor -> UastCallKind.CONSTRUCTOR_CALL
            isAnnotationArgumentArrayInitializer(ktCallElement, fqName) -> UastCallKind.NESTED_ARRAY_INITIALIZER
            else -> UastCallKind.METHOD_CALL
        }
    }

    override fun isAnnotationConstructorCall(ktCallElement: KtCallElement): Boolean {
        val classDescriptor = ktCallElement.resolveToClassDescriptor() ?: return false
        return classDescriptor.kind == ClassKind.ANNOTATION_CLASS
    }

    override fun resolveToClassIfConstructorCall(ktCallElement: KtCallElement, source: UElement): PsiClass? {
        return resolveToClassIfConstructorCallImpl(ktCallElement, source) as? PsiClass
    }

    override fun resolveToClass(ktAnnotationEntry: KtAnnotationEntry, source: UElement): PsiClass? {
        val classDescriptor = ktAnnotationEntry.resolveToClassDescriptor() ?: return null
        return ktAnnotationEntry.calleeExpression?.let { ktExpression ->
            resolveToDeclarationImpl(ktExpression, classDescriptor) as? PsiClass
        }
    }

    private fun <T : KtCallElement> T.resolveToClassDescriptor(): ClassDescriptor? =
        when (this) {
            is KtAnnotationEntry ->
                this.analyze()[BindingContext.ANNOTATION, this]?.annotationClass
            is KtCallExpression ->
                (this.getResolvedCall(this.analyze())?.resultingDescriptor as? ClassConstructorDescriptor)?.constructedClass
            else -> null
        }

    override fun resolveToDeclaration(ktExpression: KtExpression): PsiElement? {
        if (ktExpression is KtExpressionWithLabel) {
            return ktExpression.analyze()[BindingContext.LABEL_TARGET, ktExpression.getTargetLabel()]
        }
        return resolveToDeclarationImpl(ktExpression)
    }

    override fun resolveToType(ktTypeReference: KtTypeReference, source: UElement, isBoxed: Boolean): PsiType? {
        return ktTypeReference.toPsiType(source, isBoxed)
    }

    override fun resolveToType(ktTypeReference: KtTypeReference, containingLightDeclaration: PsiModifierListOwner?): PsiType? {
        return ktTypeReference.getType()
            ?.toPsiType(containingLightDeclaration, ktTypeReference, PsiTypeConversionConfiguration.create(ktTypeReference))
    }

    override fun getReceiverType(ktCallElement: KtCallElement, source: UElement): PsiType? {
        val resolvedCall = ktCallElement.getResolvedCall(ktCallElement.analyze()) ?: return null
        val receiver = resolvedCall.dispatchReceiver ?: resolvedCall.extensionReceiver ?: return null
        return receiver.type.toPsiType(source, ktCallElement, PsiTypeConversionConfiguration.create(ktCallElement, isBoxed = true))
    }

    override fun getAccessorReceiverType(ktSimpleNameExpression: KtSimpleNameExpression, source: UElement): PsiType? {
        val resolvedCall = ktSimpleNameExpression.getResolvedCall(ktSimpleNameExpression.analyze()) ?: return null
        if (resolvedCall.resultingDescriptor !is SyntheticJavaPropertyDescriptor) return null
        val receiver = resolvedCall.dispatchReceiver ?: resolvedCall.extensionReceiver ?: return null
        return receiver.type.toPsiType(
            source,
            ktSimpleNameExpression,
            PsiTypeConversionConfiguration.create(ktSimpleNameExpression, isBoxed = true)
        )
    }

    override fun getDoubleColonReceiverType(ktDoubleColonExpression: KtDoubleColonExpression, source: UElement): PsiType? {
        val ktType =
            ktDoubleColonExpression.analyze()[BindingContext.DOUBLE_COLON_LHS, ktDoubleColonExpression.receiverExpression]?.type
                ?: return null
        return ktType.toPsiType(
            source,
            ktDoubleColonExpression,
            PsiTypeConversionConfiguration.create(ktDoubleColonExpression, isBoxed = true)
        )
    }

    override fun getCommonSupertype(left: KtExpression, right: KtExpression, uExpression: UExpression): PsiType? {
        val ktElement = uExpression.sourcePsi as? KtExpression ?: return null

        val leftType = left.analyze()[BindingContext.EXPRESSION_TYPE_INFO, left]?.type ?: return null
        val rightType = right.analyze()[BindingContext.EXPRESSION_TYPE_INFO, right]?.type ?: return null

        return CommonSupertypes
            .commonSupertype(listOf(leftType, rightType))
            .toPsiType(uExpression, ktElement, PsiTypeConversionConfiguration.create(ktElement))
    }

    override fun getType(ktExpression: KtExpression, source: UElement): PsiType? {
        return when (ktExpression) {
            is KtArrayAccessExpression -> {
                getExpressionType(ktExpression, source) ?: run {
                    // for unknown reason in assignment position there is no `EXPRESSION_TYPE_INFO` so we getting it from the array type
                    val arrayExpression = ktExpression.arrayExpression ?: return null
                    val arrayType = arrayExpression.analyze()[BindingContext.EXPRESSION_TYPE_INFO, arrayExpression]?.type ?: return null
                    return arrayType.arguments.firstOrNull()?.type
                        ?.toPsiType(source, arrayExpression, PsiTypeConversionConfiguration.create(arrayExpression))
                }
            }
            else -> getExpressionType(ktExpression, source)
        }
    }

    private fun getExpressionType(ktExpression: KtExpression, source: UElement): PsiType? {
        val ktType = ktExpression.analyze()[BindingContext.EXPRESSION_TYPE_INFO, ktExpression]?.type ?: return null
        return ktType.toPsiType(source, ktExpression, PsiTypeConversionConfiguration.create(ktExpression))
    }

    override fun getType(ktDeclaration: KtDeclaration, source: UElement): PsiType? {
        val returnType = (ktDeclaration.analyze()[BindingContext.DECLARATION_TO_DESCRIPTOR, ktDeclaration] as? CallableDescriptor)
            ?.returnType
            ?: return null
        return returnType.toPsiType(
            source,
            ktDeclaration,
            PsiTypeConversionConfiguration.create(
                ktDeclaration,
                isBoxed = returnType.isMarkedNullable,
            )
        )
    }

    override fun getType(
        ktDeclaration: KtDeclaration,
        containingLightDeclaration: PsiModifierListOwner?,
        isForFake: Boolean,
    ): PsiType? {
        val returnType = (ktDeclaration.analyze()[BindingContext.DECLARATION_TO_DESCRIPTOR, ktDeclaration] as? CallableDescriptor)
            ?.returnType
            ?: return null
        return returnType.toPsiType(
            containingLightDeclaration,
            ktDeclaration,
            PsiTypeConversionConfiguration.create(
                ktDeclaration,
                isBoxed = returnType.isMarkedNullable,
                isForFake = isForFake,
            )
        )
    }

    override fun getFunctionType(ktFunction: KtFunction, source: UElement?): PsiType? {
        if (ktFunction is KtConstructor<*>) return null
        val descriptor = ktFunction.analyze()[BindingContext.FUNCTION, ktFunction] ?: return null
        val returnType = descriptor.returnType ?: return null

        return createFunctionType(
            builtIns = descriptor.builtIns,
            annotations = descriptor.annotations,
            receiverType = descriptor.extensionReceiverParameter?.type,
            contextReceiverTypes = descriptor.contextReceiverParameters.map { it.type },
            parameterTypes = descriptor.valueParameters.map { it.type },
            parameterNames = descriptor.valueParameters.map { it.name },
            returnType = returnType
        ).toPsiType(source, ktFunction, PsiTypeConversionConfiguration.create(ktFunction))
    }

    override fun getFunctionalInterfaceType(uLambdaExpression: KotlinULambdaExpression): PsiType? {
        return uLambdaExpression.getFunctionalInterfaceType()
    }

    override fun nullability(psiElement: PsiElement): KtTypeNullability? {
        return getTargetType(psiElement)?.nullability()?.let {
            when (it) {
                TypeNullability.NOT_NULL -> KtTypeNullability.NON_NULLABLE
                TypeNullability.NULLABLE -> KtTypeNullability.NULLABLE
                TypeNullability.FLEXIBLE -> KtTypeNullability.UNKNOWN
            }
        }
    }

    private fun getTargetType(annotatedElement: PsiElement): KotlinType? {
        if (annotatedElement is KtTypeReference) {
            annotatedElement.getType()?.let { return it }
        }
        if (annotatedElement is KtCallableDeclaration) {
            annotatedElement.typeReference?.getType()?.let { return it }
            annotatedElement.getReturnType()?.let { return it }
        }
        if (annotatedElement is KtProperty) {
            annotatedElement.initializer?.let { it.getType(it.analyze()) }?.let { return it }
            annotatedElement.delegateExpression?.let { it.getType(it.analyze())?.arguments?.firstOrNull()?.type }?.let { return it }
        }
        annotatedElement.getParentOfType<KtProperty>(false)?.let {
            it.typeReference?.getType() ?: it.initializer?.let { it.getType(it.analyze()) }
        }?.let { return it }
        return null
    }

    override fun evaluate(uExpression: UExpression): Any? {
        val ktElement = uExpression.sourcePsi as? KtExpression ?: return null
        val compileTimeConst = ktElement.analyze()[BindingContext.COMPILE_TIME_VALUE, ktElement]
        if (compileTimeConst is UnsignedErrorValueTypeConstant) return null
        val ktType = ktElement.analyze()[BindingContext.EXPRESSION_TYPE_INFO, ktElement]?.type ?: TypeUtils.NO_EXPECTED_TYPE
        return compileTimeConst?.takeUnless { it.isError }?.getValue(ktType)
    }
}
