// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.fir.fe10

import org.jetbrains.kotlin.analysis.api.KtStarTypeProjection
import org.jetbrains.kotlin.analysis.api.KtTypeArgumentWithVariance
import org.jetbrains.kotlin.analysis.api.KtTypeProjection
import org.jetbrains.kotlin.analysis.api.annotations.annotations
import org.jetbrains.kotlin.analysis.api.symbols.KtAnonymousObjectSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtNamedClassOrObjectSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtTypeAliasSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtTypeParameterSymbol
import org.jetbrains.kotlin.analysis.api.types.*
import org.jetbrains.kotlin.builtins.DefaultBuiltIns
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.incremental.components.LookupLocation
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter
import org.jetbrains.kotlin.resolve.scopes.MemberScope
import org.jetbrains.kotlin.types.*
import org.jetbrains.kotlin.types.checker.KotlinTypeRefiner
import org.jetbrains.kotlin.types.error.ErrorTypeKind
import org.jetbrains.kotlin.types.error.ErrorUtils
import org.jetbrains.kotlin.utils.Printer
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

abstract class KtSymbolBasedAbstractTypeConstructor<T> internal constructor(
    val ktSBDescriptor: T
) : ClassifierBasedTypeConstructor() where T : KtSymbolBasedDeclarationDescriptor, T : ClassifierDescriptor {
    override fun getDeclarationDescriptor(): ClassifierDescriptor = ktSBDescriptor

    // TODO: captured types
    override fun isDenotable(): Boolean = true

    // for Intention|inspection it shouldn't be important what to use.
    override fun getBuiltIns(): KotlinBuiltIns = DefaultBuiltIns.Instance

    // I don't think that we need to implement this method
    override fun isFinal(): Boolean = ktSBDescriptor.context.implementationPostponed("ktSBDescriptor = $ktSBDescriptor")

    @TypeRefinement
    override fun refine(kotlinTypeRefiner: KotlinTypeRefiner): TypeConstructor =
        ktSBDescriptor.context.noImplementation("ktSBDescriptor = $ktSBDescriptor")
}

class KtSymbolBasedClassTypeConstructor(ktSBDescriptor: KtSymbolBasedClassDescriptor) :
    KtSymbolBasedAbstractTypeConstructor<KtSymbolBasedClassDescriptor>(ktSBDescriptor) {
    override fun getParameters(): List<TypeParameterDescriptor> =
        ktSBDescriptor.ktSymbol.typeParameters.map { KtSymbolBasedTypeParameterDescriptor(it, ktSBDescriptor.context) }

    override fun getSupertypes(): Collection<KotlinType> =
        ktSBDescriptor.ktSymbol.superTypes.map { it.toKotlinType(ktSBDescriptor.context) }

    override fun isSameClassifier(classifier: ClassifierDescriptor): Boolean {
        return classifier is ClassDescriptor && areFqNamesEqual(declarationDescriptor, classifier)
    }

    override fun toString() = DescriptorUtils.getFqName(ktSBDescriptor).asString()
}

class KtSymbolBasedTypeParameterTypeConstructor(ktSBDescriptor: KtSymbolBasedTypeParameterDescriptor) :
    KtSymbolBasedAbstractTypeConstructor<KtSymbolBasedTypeParameterDescriptor>(ktSBDescriptor) {
    override fun getParameters(): List<TypeParameterDescriptor> = emptyList()

    override fun getSupertypes(): Collection<KotlinType> =
        ktSBDescriptor.ktSymbol.upperBounds.map { it.toKotlinType(ktSBDescriptor.context) }

    // TODO overrides: see AbstractTypeParameterDescriptor.TypeParameterTypeConstructor.isSameClassifier
    override fun isSameClassifier(classifier: ClassifierDescriptor): Boolean = ktSBDescriptor == classifier

    override fun toString(): String = ktSBDescriptor.name.asString()
}

// This class is not suppose to be used as "is instance of" because scopes could be wrapped into other scopes
// so generally it isn't a good idea
internal class MemberScopeForKtSymbolBasedDescriptors(lazyDebugInfo: () -> String) : MemberScope {
    private val additionalInfo by lazy(lazyDebugInfo)

    private fun noImplementation(): Nothing =
        error("Scope for descriptors based on KtSymbols should not be used, additional info: $additionalInfo")

    override fun getContributedVariables(name: Name, location: LookupLocation): Collection<PropertyDescriptor> = noImplementation()
    override fun getContributedFunctions(name: Name, location: LookupLocation): Collection<SimpleFunctionDescriptor> = noImplementation()
    override fun getFunctionNames(): Set<Name> = noImplementation()
    override fun getVariableNames(): Set<Name> = noImplementation()
    override fun getClassifierNames(): Set<Name> = noImplementation()
    override fun printScopeStructure(p: Printer): Unit = noImplementation()
    override fun getContributedClassifier(name: Name, location: LookupLocation): ClassifierDescriptor = noImplementation()

    override fun getContributedDescriptors(
        kindFilter: DescriptorKindFilter,
        nameFilter: (Name) -> Boolean
    ): Collection<DeclarationDescriptor> = noImplementation()
}

fun KtType.getDescriptorsAnnotations(context: Fe10WrapperContext): Annotations =
    Annotations.create(annotations.map { KtSymbolBasedAnnotationDescriptor(it, context) })

fun KtTypeProjection.toTypeProjection(context: Fe10WrapperContext): TypeProjection =
    when (this) {
        is KtStarTypeProjection -> StarProjectionForAbsentTypeParameter(context.builtIns)
        is KtTypeArgumentWithVariance -> TypeProjectionImpl(variance, type.toKotlinType(context))
    }

fun KtType.toKotlinType(context: Fe10WrapperContext, annotations: Annotations = getDescriptorsAnnotations(context)): UnwrappedType {
    val typeConstructor: TypeConstructor = when (this) {
        is KtTypeParameterType -> KtSymbolBasedTypeParameterDescriptor(this.symbol, context).typeConstructor
        is KtNonErrorClassType -> when (val classLikeSymbol = classSymbol) {
            is KtTypeAliasSymbol -> return classLikeSymbol.toExpandedKotlinType(context, ownTypeArguments, annotations)
            is KtNamedClassOrObjectSymbol -> KtSymbolBasedClassDescriptor(classLikeSymbol, context).typeConstructor
            is KtAnonymousObjectSymbol -> context.implementationPostponed()
        }
        is KtErrorType -> ErrorUtils.createErrorTypeConstructor(ErrorTypeKind.TYPE_FOR_ERROR_TYPE_CONSTRUCTOR, errorMessage)
        is KtFlexibleType -> {
            return KotlinTypeFactory.flexibleType(
                lowerBound.toKotlinType(context, annotations) as SimpleType,
                upperBound.toKotlinType(context, annotations) as SimpleType
            )
        }

        is KtIntersectionType -> {
            // most likely it isn't correct and intersectTypes(List<UnwrappedType>) should be used,
            // but I don't think that we will have the real problem with that implementation
            return IntersectionTypeConstructor(conjuncts.map { it.toKotlinType(context) }).createType()
        }
        is KtDefinitelyNotNullType -> {
            val kotlinType = original.toKotlinType(context, annotations)
            return DefinitelyNotNullType.makeDefinitelyNotNull(kotlinType) ?: kotlinType
        }
        is KtDynamicType -> {
            return createDynamicType(context.builtIns)
        }
        else -> error("Unexpected subclass: ${this.javaClass}")
    }

    val ktTypeArguments = this.safeAs<KtNonErrorClassType>()?.ownTypeArguments ?: emptyList()

    val markedAsNullable = this.nullability == KtTypeNullability.NULLABLE

    return KotlinTypeFactory.simpleTypeWithNonTrivialMemberScope(
        annotations.toDefaultAttributes(), typeConstructor, ktTypeArguments.map { it.toTypeProjection(context) }, markedAsNullable,
        MemberScopeForKtSymbolBasedDescriptors { this.asStringForDebugging() }
    )
}

fun KtTypeAliasSymbol.toExpandedKotlinType(
    context: Fe10WrapperContext,
    arguments: List<KtTypeProjection>,
    annotations: Annotations
): UnwrappedType {
    check(arguments.size == typeParameters.size) {
        "${arguments.size} != ${typeParameters.size}"
    }

    val expandedUnsubstitutedType = expandedType.toKotlinType(context, annotations)
    if (typeParameters.isEmpty()) return expandedUnsubstitutedType

    // KtSubstitutor isn't able to substitute TypeProjections KT-53095
    val map = mutableMapOf<KtTypeParameterSymbol, TypeProjection>()

    typeParameters.forEachIndexed { index, ktTypeParameterSymbol ->
        map[ktTypeParameterSymbol] = arguments[index].toTypeProjection(context)
    }

    return Fe10BindingSimpleTypeSubstitutor.substitute(map, expandedUnsubstitutedType)
}
