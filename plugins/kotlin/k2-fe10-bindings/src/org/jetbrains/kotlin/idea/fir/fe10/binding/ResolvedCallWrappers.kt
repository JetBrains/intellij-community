// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.fe10.binding

import org.jetbrains.kotlin.analysis.api.calls.*
import org.jetbrains.kotlin.analysis.api.diagnostics.KtDiagnostic
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KtFirDiagnostic
import org.jetbrains.kotlin.analysis.api.symbols.KtFunctionLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtReceiverParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtValueParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtVariableLikeSymbol
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.TypeParameterDescriptor
import org.jetbrains.kotlin.descriptors.ValueParameterDescriptor
import org.jetbrains.kotlin.idea.fir.fe10.Fe10WrapperContext
import org.jetbrains.kotlin.idea.fir.fe10.toDeclarationDescriptor
import org.jetbrains.kotlin.idea.fir.fe10.toKotlinType
import org.jetbrains.kotlin.psi.Call
import org.jetbrains.kotlin.psi.ValueArgument
import org.jetbrains.kotlin.resolve.calls.model.*
import org.jetbrains.kotlin.resolve.calls.results.ResolutionStatus
import org.jetbrains.kotlin.resolve.calls.tasks.ExplicitReceiverKind
import org.jetbrains.kotlin.resolve.scopes.receivers.CastImplicitClassReceiver
import org.jetbrains.kotlin.resolve.scopes.receivers.ExpressionReceiver
import org.jetbrains.kotlin.resolve.scopes.receivers.ImplicitClassReceiver
import org.jetbrains.kotlin.resolve.scopes.receivers.ReceiverValue
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.utils.addToStdlib.safeAs


internal abstract class Fe10WrapperResolvedCall<C : KtCallableMemberCall<*, *>>(
    private val psiCall: Call,
    protected val ktCall: C,
    private val diagnostic: KtDiagnostic?,
    protected val context: Fe10WrapperContext,
) : ResolvedCall<CallableDescriptor> {
    private val _typeArguments: Map<TypeParameterDescriptor, KotlinType> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val ktTypeParameters = ktCall.partiallyAppliedSymbol.symbol.typeParameters
        if (ktTypeParameters.isEmpty()) return@lazy emptyMap()

        val inferredTypeParametersByIndex: List<KtType?> = ktTypeParameters.map { ktCall.typeArgumentsMapping[it] }
        if (inferredTypeParametersByIndex.size != candidateDescriptor.typeParameters.size) {
            context.errorHandling("${inferredTypeParametersByIndex.size} != ${candidateDescriptor.typeParameters.size}")
        }

        val typeArguments = linkedMapOf<TypeParameterDescriptor, KotlinType>()
        for ((index, parameter) in candidateDescriptor.typeParameters.withIndex()) {
            // If inference failed, old FE don't add anything into map
            val kotlinType = inferredTypeParametersByIndex[index]?.toKotlinType(context) ?: continue
            typeArguments[parameter] = kotlinType
        }
        typeArguments
    }

    override fun getCall(): Call = psiCall

    override fun getExtensionReceiver(): ReceiverValue? = ktCall.partiallyAppliedSymbol.extensionReceiver?.asFe10ReceiverValue(context)

    override fun getDispatchReceiver(): ReceiverValue? = ktCall.partiallyAppliedSymbol.dispatchReceiver?.asFe10ReceiverValue(context)

    override fun getExplicitReceiverKind() =
        if (extensionReceiver is ExpressionReceiver) {
            if (dispatchReceiver is ExpressionReceiver) ExplicitReceiverKind.BOTH_RECEIVERS else ExplicitReceiverKind.EXTENSION_RECEIVER
        } else {
            if (dispatchReceiver is ExpressionReceiver) ExplicitReceiverKind.DISPATCH_RECEIVER else ExplicitReceiverKind.NO_EXPLICIT_RECEIVER
        }

    override fun getStatus(): ResolutionStatus {
        if (diagnostic == null) return ResolutionStatus.SUCCESS
        return when (diagnostic) {
            is KtFirDiagnostic.NamedArgumentsNotAllowed,
            is KtFirDiagnostic.NonVarargSpread,
            is KtFirDiagnostic.ArgumentPassedTwice,
            is KtFirDiagnostic.TooManyArguments,
            is KtFirDiagnostic.NoValueForParameter,
            is KtFirDiagnostic.NamedParameterNotFound,
            is KtFirDiagnostic.NameForAmbiguousParameter
            -> ResolutionStatus.ARGUMENTS_MAPPING_ERROR

            else -> context.implementationPostponed(diagnostic.toString())
        }

    }

    override fun getTypeArguments(): Map<TypeParameterDescriptor, KotlinType> = _typeArguments

    override fun getContextReceivers(): List<ReceiverValue> = context.implementationPlanned()
    override fun getDataFlowInfoForArguments(): DataFlowInfoForArguments = context.noImplementation()
    override fun getSmartCastDispatchReceiverType(): KotlinType? = context.noImplementation()
}

internal class FunctionFe10WrapperResolvedCall(psiCall: Call, call: KtFunctionCall<*>, diagnostic: KtDiagnostic?, context: Fe10WrapperContext) :
    Fe10WrapperResolvedCall<KtFunctionCall<*>>(psiCall, call, diagnostic, context) {
    private val ktFunctionSymbol: KtFunctionLikeSymbol = ktCall.partiallyAppliedSymbol.symbol

    private val argumentsMap: Map<ValueParameterDescriptor, ResolvedValueArgument> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val allArguments: List<ValueArgument> = psiCall.valueArguments + psiCall.functionLiteralArguments
        val ktParameterToResolvedValueArgument = hashMapOf<KtValueParameterSymbol, MutableList<ValueArgument>>()

        for (argument in allArguments) {
            val parameter: KtValueParameterSymbol =
                ktCall.argumentMapping[argument.getArgumentExpression()]?.symbol ?: context.errorHandling()

            ktParameterToResolvedValueArgument.getOrPut(parameter) { arrayListOf() }.add(argument)
        }

        // linkedMap is important -- used in getValueArgumentsByIndex
        val arguments = linkedMapOf<ValueParameterDescriptor, ResolvedValueArgument>()
        for ((parameterIndex, parameter) in ktFunctionSymbol.valueParameters.withIndex()) {
            val argumentsForThisParameter: List<ValueArgument> = ktParameterToResolvedValueArgument[parameter] ?: emptyList()
            val resolvedValueArgument = when(argumentsForThisParameter.size) {
                0 -> DefaultValueArgument.DEFAULT
                1 -> ExpressionValueArgument(argumentsForThisParameter.single())
                else -> VarargValueArgument(argumentsForThisParameter)
            }
            arguments[resultingDescriptor.valueParameters[parameterIndex]] = resolvedValueArgument
        }
        arguments
    }

    // For List<String>.add("") call ktFunctionSymbol is List<T>.add(T) i.e. we should substitute types from dispatch receiver
    override fun getCandidateDescriptor(): CallableDescriptor =
        context.incorrectImplementation {  ktFunctionSymbol.toDeclarationDescriptor(context) }

    // type arguments should be substituted
    override fun getResultingDescriptor(): CallableDescriptor = context.incorrectImplementation { candidateDescriptor }

    override fun getValueArguments(): Map<ValueParameterDescriptor, ResolvedValueArgument> = argumentsMap

    override fun getValueArgumentsByIndex(): List<ResolvedValueArgument> = valueArguments.values.toList()

    override fun getArgumentMapping(valueArgument: ValueArgument): ArgumentMapping {
        val ktParameter = ktCall.argumentMapping.get(valueArgument.getArgumentExpression())?.symbol
        if (ktParameter == null) return ArgumentUnmapped

        val parameterIndex = ktFunctionSymbol.valueParameters.indexOf(ktParameter)
        if (parameterIndex == -1) context.errorHandling("$ktParameter not found in $ktFunctionSymbol")

        val parameterDescriptor = resultingDescriptor.valueParameters.getOrNull(parameterIndex) ?: context.errorHandling()
        val argumentMatch = ArgumentMatchImpl(parameterDescriptor)
        context.incorrectImplementation {
            // I'm not sure, when we should have not success status
            argumentMatch.recordMatchStatus(ArgumentMatchStatus.SUCCESS)
        }
        return argumentMatch
    }
}

internal class VariableFe10WrapperResolvedCall(psiCall: Call, call: KtVariableAccessCall, diagnostic: KtDiagnostic?, context: Fe10WrapperContext) :
    Fe10WrapperResolvedCall<KtVariableAccessCall>(psiCall, call, diagnostic, context) {

    private val ktVariableSymbol: KtVariableLikeSymbol = ktCall.partiallyAppliedSymbol.symbol

    override fun getCandidateDescriptor(): CallableDescriptor = ktVariableSymbol.toDeclarationDescriptor(context) as CallableDescriptor

    // type arguments should be substituted
    override fun getResultingDescriptor(): CallableDescriptor = context.incorrectImplementation { candidateDescriptor }

    override fun getValueArguments(): Map<ValueParameterDescriptor, ResolvedValueArgument> = emptyMap()
    override fun getValueArgumentsByIndex(): List<ResolvedValueArgument> = emptyList()
    override fun getArgumentMapping(valueArgument: ValueArgument): ArgumentMapping =
        error("Variable call has no arguments. ktVariableSymbol: $ktVariableSymbol, valueArgument = $valueArgument")
}

private fun KtReceiverValue.asFe10ReceiverValue(context: Fe10WrapperContext, smartCastType: KotlinType? = null): ReceiverValue {
    return when (this) {
        is KtSmartCastedReceiverValue -> original.asFe10ReceiverValue(context, this.type.toKotlinType(context))
        is KtExplicitReceiverValue -> ExpressionReceiver.create(
            expression,
            smartCastType ?: this.type.toKotlinType(context),
            context.bindingContext
        )

        is KtImplicitReceiverValue -> {
            val ktDeclaration = symbol.safeAs<KtReceiverParameterSymbol>()?.owningCallableSymbol ?: symbol
            when (val descriptor = ktDeclaration.toDeclarationDescriptor(context)) {
                is ClassDescriptor ->
                    if (smartCastType != null) {
                        CastImplicitClassReceiver(descriptor, smartCastType)
                    } else {
                        ImplicitClassReceiver(descriptor)
                    }

                is CallableDescriptor -> descriptor.extensionReceiverParameter?.value
                    ?: context.errorHandling("$descriptor should have extension receiver")

                else -> context.errorHandling("Unexpected descriptor: $descriptor")
            }
        }
    }
}
