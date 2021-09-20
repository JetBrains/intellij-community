/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.uast.kotlin.internal

import org.jetbrains.kotlin.builtins.functions.FunctionClassDescriptor
import org.jetbrains.kotlin.builtins.functions.FunctionClassKind
import org.jetbrains.kotlin.builtins.isSuspendFunctionTypeOrSubtype
import org.jetbrains.kotlin.builtins.jvm.JavaToKotlinClassMap
import org.jetbrains.kotlin.codegen.signature.JvmSignatureWriter
import org.jetbrains.kotlin.codegen.state.isMostPreciseContravariantArgument
import org.jetbrains.kotlin.codegen.state.isMostPreciseCovariantArgument
import org.jetbrains.kotlin.codegen.state.updateArgumentModeFromAnnotations
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.load.kotlin.TypeMappingMode
import org.jetbrains.kotlin.name.SpecialNames
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.types.*
import org.jetbrains.kotlin.types.checker.SimpleClassicTypeSystemContext
import org.jetbrains.kotlin.types.checker.convertVariance
import org.jetbrains.kotlin.types.model.*
import org.jetbrains.kotlin.types.typeUtil.representativeUpperBound
import org.jetbrains.kotlin.types.typeUtil.requiresTypeAliasExpansion
import org.jetbrains.org.objectweb.asm.Type

internal object KotlinUastTypeMapper : TypeMappingContext<JvmSignatureWriter> {
    override val typeContext: TypeSystemCommonBackendContextForTypeMapping =
        KotlinUastTypeSystemCommonBackendContextForTypeMapping()

    override fun getClassInternalName(typeConstructor: TypeConstructorMarker): String {
        require(typeConstructor is ClassifierBasedTypeConstructor)
        val simpleType = typeConstructor.declarationDescriptor.defaultType
        return simpleType.constructor.toString() // TODO: not fq name, e.g., kotlin.Unit -> Unit
    }

    override fun getScriptInternalName(typeConstructor: TypeConstructorMarker): String {
        TODO("Not yet implemented")
    }

    override fun JvmSignatureWriter.writeGenericType(type: KotlinTypeMarker, asmType: Type, mode: TypeMappingMode) {
        require(type is KotlinType)
        // Nothing mapping rules:
        //  Map<Nothing, Foo> -> Map
        //  Map<Foo, List<Nothing>> -> Map<Foo, List>
        //  In<Nothing, Foo> == In<*, Foo> -> In<?, Foo>
        //  In<Nothing, Nothing> -> In
        //  Inv<in Nothing, Foo> -> Inv
        if (skipGenericSignature() || hasNothingInNonContravariantPosition(type) || type.arguments.isEmpty()) {
            writeAsmType(asmType)
            return
        }

        val possiblyInnerType = type.buildPossiblyInnerType() ?: error("possiblyInnerType with arguments should not be null")

        val innerTypesAsList = possiblyInnerType.segments()

        val indexOfParameterizedType = innerTypesAsList.indexOfFirst { innerPart -> innerPart.arguments.isNotEmpty() }
        if (indexOfParameterizedType < 0 || innerTypesAsList.size == 1) {
            writeClassBegin(asmType)
            writeGenericArguments(this, possiblyInnerType, mode)
        } else {
            val outerType = innerTypesAsList[indexOfParameterizedType]

            writeOuterClassBegin(asmType, mapType(outerType.classDescriptor.defaultType, this).internalName)
            writeGenericArguments(this, outerType, mode)

            writeInnerParts(innerTypesAsList, this, mode, indexOfParameterizedType + 1) // inner parts separated by `.`
        }

        writeClassEnd()
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
                classDescriptor.functionKind == FunctionClassKind.KFunction ||
                classDescriptor.functionKind == FunctionClassKind.KSuspendFunction
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

                if (parameterVariance == Variance.IN_VARIANCE && isMostPreciseContravariantArgument(projection.getType(), parameter)) {
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

    fun mapType(type: KotlinType, sw: JvmSignatureWriter? = null, mode: TypeMappingMode = TypeMappingMode.DEFAULT): Type {
        val expandedType = if (type.requiresTypeAliasExpansion()) {
            (type.constructor.declarationDescriptor as? TypeAliasDescriptor)?.expandedType ?: type
        } else type
        return AbstractTypeMapper.mapType(this, expandedType.lowerIfFlexible(), mode, sw)
    }
}

private class KotlinUastTypeSystemCommonBackendContextForTypeMapping(
) : TypeSystemCommonBackendContext by SimpleClassicTypeSystemContext, TypeSystemCommonBackendContextForTypeMapping {
    override fun continuationTypeConstructor(): TypeConstructorMarker {
        TODO("Not yet implemented")
    }

    override fun functionNTypeConstructor(n: Int): TypeConstructorMarker {
        TODO("Not yet implemented")
    }

    override fun TypeConstructorMarker.defaultType(): KotlinTypeMarker {
        require(this is ClassifierBasedTypeConstructor)
        return this.declarationDescriptor.defaultType
    }

    override fun SimpleTypeMarker.isKClass(): Boolean {
        require(this is KotlinType)
        TODO("Not yet implemented")
    }

    override fun KotlinTypeMarker.isRawType(): Boolean {
        require(this is KotlinType)
        return this is RawType
    }

    override fun TypeConstructorMarker.isScript(): Boolean {
        return false
    }

    override fun SimpleTypeMarker.isSuspendFunction(): Boolean {
        require(this is KotlinType)
        return this.isSuspendFunctionTypeOrSubtype
    }

    override fun TypeConstructorMarker.isTypeParameter(): Boolean {
        return (this as? ClassifierBasedTypeConstructor)?.declarationDescriptor is TypeParameterDescriptor
    }

    override fun TypeConstructorMarker.asTypeParameter(): TypeParameterMarker {
        require(isTypeParameter())
        return (this as ClassifierBasedTypeConstructor).declarationDescriptor as TypeParameterDescriptor
    }

    override fun TypeParameterMarker.representativeUpperBound(): KotlinTypeMarker {
        require(this is TypeParameterDescriptor)
        return representativeUpperBound
    }

    override fun TypeConstructorMarker.typeWithArguments(arguments: List<KotlinTypeMarker>): SimpleTypeMarker {
        require(this is KotlinType)
        TODO("Not yet implemented")
    }
}
