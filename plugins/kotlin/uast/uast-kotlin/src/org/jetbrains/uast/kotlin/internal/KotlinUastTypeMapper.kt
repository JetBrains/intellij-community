/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.uast.kotlin.internal

import org.jetbrains.kotlin.builtins.functions.FunctionClassDescriptor
import org.jetbrains.kotlin.builtins.functions.FunctionTypeKind
import org.jetbrains.kotlin.builtins.jvm.JavaToKotlinClassMap
import org.jetbrains.kotlin.codegen.signature.AsmTypeFactory
import org.jetbrains.kotlin.codegen.signature.JvmSignatureWriter
import org.jetbrains.kotlin.codegen.state.isMostPreciseContravariantArgument
import org.jetbrains.kotlin.codegen.state.isMostPreciseCovariantArgument
import org.jetbrains.kotlin.codegen.state.updateArgumentModeFromAnnotations
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.load.kotlin.TypeMappingConfiguration
import org.jetbrains.kotlin.load.kotlin.TypeMappingMode
import org.jetbrains.kotlin.load.kotlin.mapType
import org.jetbrains.kotlin.name.SpecialNames
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.types.*
import org.jetbrains.kotlin.types.checker.SimpleClassicTypeSystemContext
import org.jetbrains.kotlin.types.checker.convertVariance
import org.jetbrains.kotlin.types.model.*
import org.jetbrains.org.objectweb.asm.Type

object KotlinUastTypeMapper {
    private val staticTypeMappingConfiguration = object : TypeMappingConfiguration<Type> {
        override fun commonSupertype(types: Collection<KotlinType>): KotlinType {
            return CommonSupertypes.commonSupertype(types)
        }

        override fun getPredefinedTypeForClass(classDescriptor: ClassDescriptor): Type? {
            return null
        }

        override fun getPredefinedInternalNameForClass(classDescriptor: ClassDescriptor): String? {
            return null
        }

        override fun processErrorType(kotlinType: KotlinType, descriptor: ClassDescriptor) {
            // Light class mode: not generating body, and thus not report error type.
        }

        override fun preprocessType(kotlinType: KotlinType): KotlinType? {
            return null
        }
    }

    fun mapType(
        type: KotlinType,
        signatureVisitor: JvmSignatureWriter? = null,
        mode: TypeMappingMode = TypeMappingMode.DEFAULT_UAST,
    ): Type {
        return mapType(
            type, AsmTypeFactory, mode, staticTypeMappingConfiguration, signatureVisitor
        ) { ktType, asmType, typeMappingMode ->
            writeGenericType(ktType, asmType, signatureVisitor, typeMappingMode)
        }
    }

    private fun mapType(descriptor: ClassifierDescriptor): Type {
        return mapType(descriptor.defaultType)
    }

    private fun writeGenericType(
        type: KotlinType,
        asmType: Type,
        signatureVisitor: JvmSignatureWriter?,
        mode: TypeMappingMode
    ) {
        if (signatureVisitor == null) return

        // Nothing mapping rules:
        //  Map<Nothing, Foo> -> Map
        //  Map<Foo, List<Nothing>> -> Map<Foo, List>
        //  In<Nothing, Foo> == In<*, Foo> -> In<?, Foo>
        //  In<Nothing, Nothing> -> In
        //  Inv<in Nothing, Foo> -> Inv
        if (signatureVisitor.skipGenericSignature() || hasNothingInNonContravariantPosition(type) || type.arguments.isEmpty()) {
            signatureVisitor.writeAsmType(asmType)
            return
        }

        val possiblyInnerType = type.buildPossiblyInnerType() ?: error("possiblyInnerType with arguments should not be null")

        val innerTypesAsList = possiblyInnerType.segments()

        val indexOfParameterizedType = innerTypesAsList.indexOfFirst { innerPart -> innerPart.arguments.isNotEmpty() }
        if (indexOfParameterizedType < 0 || innerTypesAsList.size == 1) {
            signatureVisitor.writeClassBegin(asmType)
            writeGenericArguments(signatureVisitor, possiblyInnerType, mode)
        } else {
            val outerType = innerTypesAsList[indexOfParameterizedType]

            signatureVisitor.writeOuterClassBegin(asmType, mapType(outerType.classDescriptor).internalName)
            writeGenericArguments(signatureVisitor, outerType, mode)

            writeInnerParts(innerTypesAsList, signatureVisitor, mode, indexOfParameterizedType + 1) // inner parts separated by `.`
        }

        signatureVisitor.writeClassEnd()
    }

    private fun hasNothingInNonContravariantPosition(kotlinType: KotlinType): Boolean =
        SimpleClassicTypeSystemContext.hasNothingInNonContravariantPosition(kotlinType)

    private fun TypeSystemContext.hasNothingInNonContravariantPosition(type: KotlinTypeMarker): Boolean {
        val typeConstructor = type.typeConstructor()

        for (i in 0 until type.argumentsCount()) {
            val projection = type.getArgument(i)
            if (projection.isStarProjection()) continue

            val argument = projection.getType()

            if (argument.isNullableNothing() ||
                argument.isNothing() && typeConstructor.getParameter(i).getVariance() != TypeVariance.IN
            ) return true
        }

        return false
    }

    private fun writeInnerParts(
        innerTypesAsList: List<PossiblyInnerType>,
        signatureVisitor: JvmSignatureWriter,
        mode: TypeMappingMode,
        index: Int
    ) {
        for (innerPart in innerTypesAsList.subList(index, innerTypesAsList.size)) {
            signatureVisitor.writeInnerClass(getJvmShortName(innerPart.classDescriptor))
            writeGenericArguments(signatureVisitor, innerPart, mode)
        }
    }

    private fun writeGenericArguments(
        signatureVisitor: JvmSignatureWriter,
        type: PossiblyInnerType,
        mode: TypeMappingMode
    ) {
        val classDescriptor = type.classDescriptor
        val parameters = classDescriptor.declaredTypeParameters
        val arguments = type.arguments

        if (classDescriptor is FunctionClassDescriptor) {
            if (classDescriptor.hasBigArity ||
                classDescriptor.functionTypeKind == FunctionTypeKind.KFunction ||
                classDescriptor.functionTypeKind == FunctionTypeKind.KSuspendFunction
            ) {
                // kotlin.reflect.KFunction{n}<P1, ..., Pn, R> is mapped to kotlin.reflect.KFunction<R> (for all n), and
                // kotlin.Function{n}<P1, ..., Pn, R> is mapped to kotlin.jvm.functions.FunctionN<R> (for n > 22).
                // So for these classes, we need to skip all type arguments except the very last one
                writeGenericArguments(signatureVisitor, listOf(arguments.last()), listOf(parameters.last()), mode)
                return
            }
        }

        writeGenericArguments(signatureVisitor, arguments, parameters, mode)
    }

    private fun writeGenericArguments(
        signatureVisitor: JvmSignatureWriter,
        arguments: List<TypeProjection>,
        parameters: List<TypeParameterDescriptor>,
        mode: TypeMappingMode
    ) {
        with(SimpleClassicTypeSystemContext) {
            writeGenericArguments(signatureVisitor, arguments, parameters, mode) { type, sw, mode ->
                mapType(type as KotlinType, sw, mode)
            }
        }
    }

    private fun TypeSystemCommonBackendContext.writeGenericArguments(
        signatureVisitor: JvmSignatureWriter,
        arguments: List<TypeArgumentMarker>,
        parameters: List<TypeParameterMarker>,
        mode: TypeMappingMode,
        mapType: (KotlinTypeMarker, JvmSignatureWriter, TypeMappingMode) -> Type
    ) {
        for ((parameter, argument) in parameters.zip(arguments)) {
            if (argument.isStarProjection() ||
                // In<Nothing, Foo> == In<*, Foo> -> In<?, Foo>
                argument.getType().isNothing() && parameter.getVariance() == TypeVariance.IN
            ) {
                signatureVisitor.writeUnboundedWildcard()
            } else {
                val argumentMode = mode.updateArgumentModeFromAnnotations(argument.getType(), this)
                val projectionKind = getVarianceForWildcard(parameter, argument, argumentMode)

                signatureVisitor.writeTypeArgument(projectionKind)

                mapType(
                    argument.getType(), signatureVisitor,
                    argumentMode.toGenericArgumentMode(
                        getEffectiveVariance(parameter.getVariance().convertVariance(), argument.getVariance().convertVariance())
                    )
                )

                signatureVisitor.writeTypeArgumentEnd()
            }
        }
    }

    private fun TypeSystemCommonBackendContext.getVarianceForWildcard(
        parameter: TypeParameterMarker, projection: TypeArgumentMarker, mode: TypeMappingMode
    ): Variance {
        val projectionKind = projection.getVariance().convertVariance()
        val parameterVariance = parameter.getVariance().convertVariance()

        if (parameterVariance == Variance.INVARIANT) {
            return projectionKind
        }

        if (mode.skipDeclarationSiteWildcards) {
            return Variance.INVARIANT
        }

        if (projectionKind == Variance.INVARIANT || projectionKind == parameterVariance) {
            if (mode.skipDeclarationSiteWildcardsIfPossible && !projection.isStarProjection()) {
                if (parameterVariance == Variance.OUT_VARIANCE && isMostPreciseCovariantArgument(projection.getType())) {
                    return Variance.INVARIANT
                }

                if (parameterVariance == Variance.IN_VARIANCE && isMostPreciseContravariantArgument(projection.getType())) {
                    return Variance.INVARIANT
                }
            }
            return parameterVariance
        }

        // In<out X> = In<*>
        // Out<in X> = Out<*>
        return Variance.OUT_VARIANCE
    }

    private fun getJvmShortName(klass: ClassDescriptor): String {
        return JavaToKotlinClassMap.mapKotlinToJava(DescriptorUtils.getFqName(klass))?.shortClassName?.asString()
            ?: SpecialNames.safeIdentifier(klass.name).identifier
    }
}
