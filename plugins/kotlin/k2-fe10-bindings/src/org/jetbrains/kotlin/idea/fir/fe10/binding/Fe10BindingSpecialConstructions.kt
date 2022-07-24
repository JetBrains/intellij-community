/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.kotlin.idea.fir.fe10.binding

import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.TypeParameterDescriptor
import org.jetbrains.kotlin.descriptors.ValueParameterDescriptor
import org.jetbrains.kotlin.idea.fir.fe10.Fe10WrapperContext
import org.jetbrains.kotlin.psi.Call
import org.jetbrains.kotlin.psi.ValueArgument
import org.jetbrains.kotlin.resolve.calls.model.*
import org.jetbrains.kotlin.resolve.calls.results.ResolutionStatus
import org.jetbrains.kotlin.resolve.calls.tasks.ExplicitReceiverKind
import org.jetbrains.kotlin.resolve.scopes.receivers.ReceiverValue
import org.jetbrains.kotlin.storage.LockBasedStorageManager
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.TypeSubstitutor
import org.jetbrains.kotlin.types.expressions.ControlStructureTypingUtils
import org.jetbrains.kotlin.types.expressions.ControlStructureTypingUtils.createFunctionDescriptorForSpecialConstruction
import org.jetbrains.kotlin.types.typeUtil.asTypeProjection

class Fe10BindingSpecialConstructionsWrappers(val context: Fe10WrapperContext) {
    val EXCL_EXCL: FunctionDescriptor = run {
        createFunctionDescriptorForSpecialConstruction(
            ControlStructureTypingUtils.ResolveConstruct.EXCL_EXCL,
            listOf("baseExpr"),
            listOf(true),
            context.moduleDescriptor,
            LockBasedStorageManager.NO_LOCKS
        )
    }
}

// special calls have exactly one type parameter
class Fe10BindingSpecialConstructionResolvedCall(
    private val psiCall: Call,
    private val inferredTypeParameter: KotlinType?,
    private val descriptor: FunctionDescriptor,
    private val context: Fe10WrapperContext
) : ResolvedCall<FunctionDescriptor> {

    init {
      check(psiCall.valueArguments.size == descriptor.valueParameters.size) {
          "${psiCall.valueArguments.size} != ${descriptor.valueParameters.size}"
      }
    }
    
    private val typeArgument get() = inferredTypeParameter ?: descriptor.typeParameters.single().defaultType

    private val _resultingDescriptor = run {
        val substitutionMap = mapOf(descriptor.typeParameters.single().typeConstructor to typeArgument.asTypeProjection())
        descriptor.substitute(TypeSubstitutor.create(substitutionMap))!!
    }


    override fun getExtensionReceiver(): ReceiverValue? = null
    override fun getDispatchReceiver(): ReceiverValue? = null
    override fun getContextReceivers(): List<ReceiverValue> = emptyList()
    override fun getExplicitReceiverKind(): ExplicitReceiverKind = ExplicitReceiverKind.NO_EXPLICIT_RECEIVER

    override fun getStatus(): ResolutionStatus =
        if (inferredTypeParameter == null) ResolutionStatus.INCOMPLETE_TYPE_INFERENCE else ResolutionStatus.SUCCESS

    override fun getCall(): Call = psiCall

    override fun getCandidateDescriptor(): FunctionDescriptor = descriptor

    override fun getResultingDescriptor(): FunctionDescriptor = _resultingDescriptor


    override fun getValueArguments(): Map<ValueParameterDescriptor, ResolvedValueArgument> =
        linkedMapOf<ValueParameterDescriptor, ResolvedValueArgument>().apply {
            for ((index, parameter) in resultingDescriptor.valueParameters.withIndex()) {
                put(parameter, ExpressionValueArgument(psiCall.valueArguments[index]))
            }
        }

    override fun getValueArgumentsByIndex(): List<ResolvedValueArgument> = psiCall.valueArguments.map(::ExpressionValueArgument)

    override fun getArgumentMapping(valueArgument: ValueArgument): ArgumentMapping {
        val index = psiCall.valueArguments.indexOf(valueArgument)
        if (index == -1) return ArgumentUnmapped
        return ArgumentMatchImpl(resultingDescriptor.valueParameters[index])
    }

    override fun getTypeArguments(): Map<TypeParameterDescriptor, KotlinType> =
        mapOf(descriptor.typeParameters.single() to typeArgument)

    override fun getDataFlowInfoForArguments(): DataFlowInfoForArguments = context.noImplementation()
    override fun getSmartCastDispatchReceiverType(): KotlinType = context.noImplementation()
}