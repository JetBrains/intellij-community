// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.fir.fe10

import org.jetbrains.kotlin.analysis.api.KtConstantInitializerValue
import org.jetbrains.kotlin.analysis.api.annotations.*
import org.jetbrains.kotlin.analysis.api.base.KtConstantValue
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.symbols.KtSymbolOrigin.*
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtAnnotatedSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtNamedSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtSymbolWithTypeParameters
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.descriptors.annotations.AnnotationDescriptor
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.descriptors.impl.AbstractReceiverParameterDescriptor
import org.jetbrains.kotlin.descriptors.impl.ReceiverParameterDescriptorImpl
import org.jetbrains.kotlin.descriptors.impl.TypeAliasConstructorDescriptor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.SpecialNames
import org.jetbrains.kotlin.psi.KtPureElement
import org.jetbrains.kotlin.resolve.constants.*
import org.jetbrains.kotlin.resolve.scopes.MemberScope
import org.jetbrains.kotlin.resolve.scopes.receivers.ExtensionReceiver
import org.jetbrains.kotlin.resolve.scopes.receivers.ImplicitClassReceiver
import org.jetbrains.kotlin.resolve.scopes.receivers.ReceiverValue
import org.jetbrains.kotlin.resolve.source.toSourceElement
import org.jetbrains.kotlin.storage.StorageManager
import org.jetbrains.kotlin.types.*
import org.jetbrains.kotlin.utils.addToStdlib.safeAs


/**
 * List of known problems/not-implemented:
 * - getOrigin return itself all the time. Formally it should return "unsubstituted" version of itself, but it isn't clear,
 *      if we really needed that nor how to implemented it. Btw, it is relevant only to member functions/properties (unlikely, but may be to
 *      inner classes) and not for "resulting descriptor". I.e. only class/interface type parameters could be substituted in FIR,
 *      there is no "resulting descriptor" and type arguments are passed together with the target function.
 * - equals on descriptors and type constructors. It isn't clear what type of equality we should implement,
 *      will figure that later o real cases.
 * See the functions below
 */

internal fun Fe10WrapperContext.containerDeclarationImplementationPostponed(): Nothing =
    implementationPostponed("It isn't clear what we really need and how to implement it")

interface KtSymbolBasedNamed : Named {
    val ktSymbol: KtNamedSymbol
    override fun getName(): Name = ktSymbol.name
}

private fun Visibility.toDescriptorVisibility(): DescriptorVisibility =
    when (this) {
        Visibilities.Public -> DescriptorVisibilities.PUBLIC
        Visibilities.Private -> DescriptorVisibilities.PRIVATE
        Visibilities.PrivateToThis -> DescriptorVisibilities.PRIVATE_TO_THIS
        Visibilities.Protected -> DescriptorVisibilities.PROTECTED
        Visibilities.Internal -> DescriptorVisibilities.INTERNAL
        Visibilities.Local -> DescriptorVisibilities.LOCAL
        else -> error("Unknown visibility: $this")
    }

private fun KtClassKind.toDescriptorKlassKind(): ClassKind =
    when (this) {
        KtClassKind.CLASS -> ClassKind.CLASS
        KtClassKind.ENUM_CLASS -> ClassKind.ENUM_CLASS
        KtClassKind.ANNOTATION_CLASS -> ClassKind.ANNOTATION_CLASS
        KtClassKind.OBJECT, KtClassKind.COMPANION_OBJECT, KtClassKind.ANONYMOUS_OBJECT -> ClassKind.OBJECT
        KtClassKind.INTERFACE -> ClassKind.INTERFACE
    }

private fun KtSymbolOrigin.toCallableDescriptorKind(): CallableMemberDescriptor.Kind = when (this) {
    DELEGATED -> CallableMemberDescriptor.Kind.DELEGATION
    SUBSTITUTION_OVERRIDE, INTERSECTION_OVERRIDE -> CallableMemberDescriptor.Kind.FAKE_OVERRIDE
    SOURCE_MEMBER_GENERATED -> CallableMemberDescriptor.Kind.SYNTHESIZED
    else -> CallableMemberDescriptor.Kind.DECLARATION
}

private fun KtAnnotationValue.toConstantValue(): ConstantValue<*> {
    return when (this) {
        KtUnsupportedAnnotationValue -> ErrorValue.create("Unsupported annotation value")
        is KtArrayAnnotationValue -> ArrayValue(values.map { it.toConstantValue() }) { TODO() }
        is KtAnnotationApplicationValue -> TODO()
        is KtKClassAnnotationValue.KtNonLocalKClassAnnotationValue -> KClassValue(classId, arrayDimensions = 0)
        is KtKClassAnnotationValue.KtLocalKClassAnnotationValue -> TODO()
        is KtKClassAnnotationValue.KtErrorClassAnnotationValue -> ErrorValue.create("Unresolved class")
        is KtEnumEntryAnnotationValue -> {
            val callableId = callableId ?: return ErrorValue.create("Unresolved enum entry")
            val classId = callableId.classId ?: return ErrorValue.create("Unresolved enum entry")
            EnumValue(classId, callableId.callableName)
        }

        is KtConstantAnnotationValue -> constantValue.toConstantValue()
    }
}

internal fun KtConstantValue.toConstantValue(): ConstantValue<*> =
    when (this) {
        is KtConstantValue.KtErrorConstantValue -> ErrorValue.create(errorMessage)
        else -> when (constantValueKind) {
            ConstantValueKind.Null -> NullValue()
            ConstantValueKind.Boolean -> BooleanValue(value as Boolean)
            ConstantValueKind.Char -> CharValue(value as Char)
            ConstantValueKind.Byte -> ByteValue(value as Byte)
            ConstantValueKind.Short -> ShortValue(value as Short)
            ConstantValueKind.Int -> IntValue(value as Int)
            ConstantValueKind.Long -> LongValue(value as Long)
            ConstantValueKind.String -> StringValue(value as String)
            ConstantValueKind.Float -> FloatValue(value as Float)
            ConstantValueKind.Double -> DoubleValue(value as Double)
            ConstantValueKind.UnsignedByte -> UByteValue(value as Byte)
            ConstantValueKind.UnsignedShort -> UShortValue(value as Short)
            ConstantValueKind.UnsignedInt -> UIntValue(value as Int)
            ConstantValueKind.UnsignedLong -> ULongValue(value as Long)
            else -> error("Unexpected constant KtSimpleConstantValue: $value (class: ${value?.javaClass}")
        }
    }

abstract class KtSymbolBasedDeclarationDescriptor(val context: Fe10WrapperContext) : DeclarationDescriptorWithSource {
    abstract val ktSymbol: KtSymbol
    override val annotations: Annotations
        get() {
            val ktAnnotations = (ktSymbol as? KtAnnotatedSymbol)?.annotations ?: return Annotations.EMPTY
            return Annotations.create(ktAnnotations.map { KtSymbolBasedAnnotationDescriptor(it, context) })
        }

    override fun getSource(): SourceElement = ktSymbol.psi.safeAs<KtPureElement>().toSourceElement()

    override fun getContainingDeclaration(): DeclarationDescriptor {
        val containerSymbol = context.withAnalysisSession {
            ktSymbol.getContainingSymbol()
        }
        if (containerSymbol != null)
            return containerSymbol.toDeclarationDescriptor(context)

        // i.e. declaration is top-level
        return KtSymbolBasedPackageFragmentDescriptor(getPackageFqNameIfTopLevel(), context)
    }

    protected abstract fun getPackageFqNameIfTopLevel(): FqName

    override fun equals(other: Any?): Boolean {
        if (other === this) return true
        if (other !is KtSymbolBasedDeclarationDescriptor) return false

        ktSymbol.psi?.let {
            return other.ktSymbol.psi == it
        }

        return ktSymbol == other.ktSymbol
    }

    override fun hashCode(): Int {
        ktSymbol.psi?.let { return it.hashCode() }
        return ktSymbol.hashCode()
    }

    override fun <R : Any?, D : Any?> accept(visitor: DeclarationDescriptorVisitor<R, D>?, data: D): R = noImplementation()
    override fun acceptVoid(visitor: DeclarationDescriptorVisitor<Void, Void>?): Unit = noImplementation()

    // stub for automatic Substitutable<T> implementation for all relevant subclasses
    fun substitute(substitutor: TypeSubstitutor): Nothing = noImplementation()

    protected fun noImplementation(): Nothing = context.noImplementation("ktSymbol = $ktSymbol")
    protected fun implementationPostponed(): Nothing = context.implementationPostponed("ktSymbol = $ktSymbol")
    protected fun implementationPlanned(): Nothing = context.implementationPlanned("ktSymbol = $ktSymbol")

    override fun toString(): String = this.javaClass.simpleName + " " + this.name
}

class KtSymbolBasedAnnotationDescriptor(
    private val ktAnnotationCall: KtAnnotationApplicationWithArgumentsInfo,
    val context: Fe10WrapperContext,
) : AnnotationDescriptor {
    override val type: KotlinType
        get() = context.implementationPlanned("ktAnnotationCall = $ktAnnotationCall")

    override val fqName: FqName?
        get() = ktAnnotationCall.classId?.asSingleFqName()

    override val allValueArguments: Map<Name, ConstantValue<*>> =
        ktAnnotationCall.arguments.associate { it.name to it.expression.toConstantValue() }

    override val source: SourceElement
        get() = ktAnnotationCall.psi.toSourceElement()

}

/**
 * Should be created only for this class receiver and extension receiver
 *
 * We are using custom equals/hashCode because in Fe10Binding all the descriptors are created multiple times,
 * so identityEquals for ReceiverParameters as in ReceiverParameterDescriptorImpl works incorrectly
 */
class Fe10BindingReceiverParameterDescriptorImpl(
    containingDeclaration: DeclarationDescriptor,
    receiverValue: ReceiverValue,
    annotations: Annotations
) : ReceiverParameterDescriptorImpl(containingDeclaration, receiverValue, annotations) {
    override fun equals(other: Any?): Boolean {
        if (other !is Fe10BindingReceiverParameterDescriptorImpl) return false
        if (other.containingDeclaration != containingDeclaration) return false

        return other.value.type == value.type
    }

    override fun hashCode(): Int = 31 * containingDeclaration.hashCode() + value.type.hashCode()
}

class KtSymbolBasedClassDescriptor(override val ktSymbol: KtNamedClassOrObjectSymbol, context: Fe10WrapperContext) :
    KtSymbolBasedDeclarationDescriptor(context), KtSymbolBasedNamed, ClassDescriptor {

    override fun isInner(): Boolean = ktSymbol.isInner
    override fun isCompanionObject(): Boolean = ktSymbol.classKind == KtClassKind.COMPANION_OBJECT
    override fun isData(): Boolean = ktSymbol.isData
    override fun isInline(): Boolean = ktSymbol.isInline // seems like th`is `flag should be removed in favor of isValue
    override fun isValue(): Boolean = ktSymbol.isInline
    override fun isFun(): Boolean = ktSymbol.isFun

    override fun isExpect(): Boolean = ktSymbol.isExpect
    override fun isActual(): Boolean = ktSymbol.isActual
    override fun isExternal(): Boolean = ktSymbol.isExternal

    override fun getVisibility(): DescriptorVisibility = ktSymbol.visibility.toDescriptorVisibility()
    override fun getModality(): Modality = ktSymbol.modality

    override fun getKind(): ClassKind = ktSymbol.classKind.toDescriptorKlassKind()

    override fun getCompanionObjectDescriptor(): ClassDescriptor? = ktSymbol.companionObject?.let {
        KtSymbolBasedClassDescriptor(it, context)
    }

    override fun getTypeConstructor(): TypeConstructor = KtSymbolBasedClassTypeConstructor(this)

    override fun getDeclaredTypeParameters(): List<TypeParameterDescriptor> = getTypeParameters(ktSymbol)

    override fun getDefaultType(): SimpleType {
        val arguments = TypeUtils.getDefaultTypeProjections(typeConstructor.parameters)
        return KotlinTypeFactory.simpleTypeWithNonTrivialMemberScope(
            TypeAttributes.Empty, typeConstructor, arguments, false,
            MemberScopeForKtSymbolBasedDescriptors { "ktSymbol = $ktSymbol" }
        )
    }

    override fun getThisAsReceiverParameter(): ReceiverParameterDescriptor =
        Fe10BindingReceiverParameterDescriptorImpl(this, ImplicitClassReceiver(this), Annotations.EMPTY)

    override fun getContextReceivers(): List<ReceiverParameterDescriptor> = implementationPlanned()

    override fun getOriginal(): ClassDescriptor = this

    override fun getUnsubstitutedPrimaryConstructor(): ClassConstructorDescriptor? = context.withAnalysisSession {
        ktSymbol.getDeclaredMemberScope().getConstructors().firstOrNull { it.isPrimary }
            ?.let { KtSymbolBasedConstructorDescriptor(it, this@KtSymbolBasedClassDescriptor) }
    }

    override fun getConstructors(): Collection<ClassConstructorDescriptor> = context.withAnalysisSession {
        ktSymbol.getDeclaredMemberScope().getConstructors().map {
            KtSymbolBasedConstructorDescriptor(it, this@KtSymbolBasedClassDescriptor)
        }.toList()
    }

    override fun getPackageFqNameIfTopLevel(): FqName = (ktSymbol.classIdIfNonLocal ?: error("should be top-level")).packageFqName

    override fun getSealedSubclasses(): Collection<ClassDescriptor> = implementationPostponed()

    override fun getValueClassRepresentation(): ValueClassRepresentation<SimpleType> = TODO("Not yet implemented")

    override fun getMemberScope(typeArguments: List<TypeProjection>): MemberScope = noImplementation()
    override fun getMemberScope(typeSubstitution: TypeSubstitution): MemberScope = noImplementation()
    override fun getUnsubstitutedMemberScope(): MemberScope = noImplementation()
    override fun getUnsubstitutedInnerClassesScope(): MemberScope = noImplementation()
    override fun getStaticScope(): MemberScope = noImplementation()

    override fun isDefinitelyNotSamInterface(): Boolean = noImplementation()
    override fun getDefaultFunctionTypeForSamInterface(): SimpleType = noImplementation()
}

class KtSymbolBasedTypeParameterDescriptor(
    override val ktSymbol: KtTypeParameterSymbol, context: Fe10WrapperContext
) : KtSymbolBasedDeclarationDescriptor(context), KtSymbolBasedNamed, TypeParameterDescriptor {
    override fun isReified(): Boolean = ktSymbol.isReified
    override fun getVariance(): Variance = ktSymbol.variance

    override fun getTypeConstructor(): TypeConstructor = KtSymbolBasedTypeParameterTypeConstructor(this)

    override fun getDefaultType(): SimpleType =
        KotlinTypeFactory.simpleTypeWithNonTrivialMemberScope(
            TypeAttributes.Empty, typeConstructor, emptyList(), false,
            MemberScopeForKtSymbolBasedDescriptors { "ktSymbol = $ktSymbol" }
        )

    override fun getUpperBounds(): List<KotlinType> = ktSymbol.upperBounds.map { it.toKotlinType(context) }
    override fun getOriginal(): TypeParameterDescriptor = this

    override fun getPackageFqNameIfTopLevel(): FqName = error("Should be called")

    // there is no such thing in FIR, and it seems like it isn't really needed for IDE and could be bypassed on client site
    override fun getIndex(): Int = implementationPostponed()

    override fun isCapturedFromOuterDeclaration(): Boolean = noImplementation()
    override fun getStorageManager(): StorageManager = noImplementation()
}

abstract class KtSymbolBasedFunctionLikeDescriptor(context: Fe10WrapperContext) :
    KtSymbolBasedDeclarationDescriptor(context), FunctionDescriptor {
    abstract override val ktSymbol: KtFunctionLikeSymbol

    override fun getReturnType(): KotlinType = ktSymbol.returnType.toKotlinType(context)

    override fun getValueParameters(): List<ValueParameterDescriptor> = ktSymbol.valueParameters.mapIndexed { index, it ->
        KtSymbolBasedValueParameterDescriptor(it, context, this, index)
    }

    override fun hasStableParameterNames(): Boolean = ktSymbol.hasStableParameterNames
    override fun hasSynthesizedParameterNames(): Boolean = implementationPostponed()

    override fun getKind(): CallableMemberDescriptor.Kind = ktSymbol.origin.toCallableDescriptorKind()

    override fun <V : Any?> getUserData(key: CallableDescriptor.UserDataKey<V>?): V? = null

    override fun getOriginal(): FunctionDescriptor = this

    override fun isHiddenForResolutionEverywhereBesideSupercalls(): Boolean = implementationPostponed()

    override fun getInitialSignatureDescriptor(): FunctionDescriptor? = noImplementation()
    override fun isHiddenToOvercomeSignatureClash(): Boolean = noImplementation()

    override fun setOverriddenDescriptors(overriddenDescriptors: MutableCollection<out CallableMemberDescriptor>) = noImplementation()
    override fun newCopyBuilder(): FunctionDescriptor.CopyBuilder<out FunctionDescriptor> = noImplementation()
    override fun copy(
        newOwner: DeclarationDescriptor?,
        modality: Modality?,
        visibility: DescriptorVisibility?,
        kind: CallableMemberDescriptor.Kind?,
        copyOverrides: Boolean
    ): FunctionDescriptor = noImplementation()
}

class KtSymbolBasedFunctionDescriptor(override val ktSymbol: KtFunctionSymbol, context: Fe10WrapperContext) :
    KtSymbolBasedFunctionLikeDescriptor(context),
    SimpleFunctionDescriptor,
    KtSymbolBasedNamed {
    override fun getExtensionReceiverParameter(): ReceiverParameterDescriptor? = getExtensionReceiverParameter(ktSymbol)
    override fun getDispatchReceiverParameter(): ReceiverParameterDescriptor? = getDispatchReceiverParameter(ktSymbol)
    override fun getContextReceiverParameters(): List<ReceiverParameterDescriptor> = implementationPlanned()

    override fun getPackageFqNameIfTopLevel(): FqName = (ktSymbol.callableIdIfNonLocal ?: error("should be top-level")).packageName

    override fun isSuspend(): Boolean = ktSymbol.isSuspend
    override fun isOperator(): Boolean = ktSymbol.isOperator
    override fun isExternal(): Boolean = ktSymbol.isExternal
    override fun isInline(): Boolean = ktSymbol.isInline
    override fun isInfix(): Boolean = implementationPostponed()
    override fun isTailrec(): Boolean = implementationPostponed()

    override fun isExpect(): Boolean = context.incorrectImplementation { false }
    override fun isActual(): Boolean = context.incorrectImplementation { false }

    override fun getVisibility(): DescriptorVisibility = ktSymbol.visibility.toDescriptorVisibility()
    override fun getModality(): Modality = ktSymbol.modality

    override fun getTypeParameters(): List<TypeParameterDescriptor> = getTypeParameters(ktSymbol)

    override fun getOverriddenDescriptors(): Collection<FunctionDescriptor> {
        val overriddenKtSymbols = context.withAnalysisSession {
            ktSymbol.getAllOverriddenSymbols()
        }

        return overriddenKtSymbols.map { KtSymbolBasedFunctionDescriptor(it as KtFunctionSymbol, context) }
    }

    override fun copy(
        newOwner: DeclarationDescriptor?,
        modality: Modality?,
        visibility: DescriptorVisibility?,
        kind: CallableMemberDescriptor.Kind?,
        copyOverrides: Boolean
    ): SimpleFunctionDescriptor = context.noImplementation()

    override fun getOriginal(): SimpleFunctionDescriptor = context.incorrectImplementation { this }

    override fun newCopyBuilder(): FunctionDescriptor.CopyBuilder<out SimpleFunctionDescriptor> =
        context.noImplementation()
}

class KtSymbolBasedConstructorDescriptor(
    override val ktSymbol: KtConstructorSymbol,
    private val ktSBClassDescriptor: KtSymbolBasedClassDescriptor
) : KtSymbolBasedFunctionLikeDescriptor(ktSBClassDescriptor.context),
    ClassConstructorDescriptor {
    override fun getName(): Name = Name.special("<init>")

    override fun getExtensionReceiverParameter(): ReceiverParameterDescriptor? = null
    override fun getDispatchReceiverParameter(): ReceiverParameterDescriptor? = getDispatchReceiverParameter(ktSymbol)
    override fun getContextReceiverParameters(): List<ReceiverParameterDescriptor> = ktSBClassDescriptor.contextReceivers

    override fun getConstructedClass(): ClassDescriptor = ktSBClassDescriptor
    override fun getContainingDeclaration(): ClassDescriptor = ktSBClassDescriptor
    override fun getPackageFqNameIfTopLevel(): FqName = error("should not be called")

    override fun isPrimary(): Boolean = ktSymbol.isPrimary

    override fun getReturnType(): KotlinType = ktSBClassDescriptor.defaultType

    override fun isSuspend(): Boolean = false
    override fun isOperator(): Boolean = false
    override fun isExternal(): Boolean = false
    override fun isInline(): Boolean = false
    override fun isInfix(): Boolean = false
    override fun isTailrec(): Boolean = false

    override fun isExpect(): Boolean = implementationPostponed()
    override fun isActual(): Boolean = implementationPostponed()

    override fun getVisibility(): DescriptorVisibility = ktSymbol.visibility.toDescriptorVisibility()
    override fun getModality(): Modality = Modality.FINAL

    override fun getTypeParameters(): List<TypeParameterDescriptor> = getTypeParameters(ktSymbol)

    override fun getOriginal(): ClassConstructorDescriptor = this

    override fun getOverriddenDescriptors(): Collection<FunctionDescriptor> = emptyList()

    override fun copy(
        newOwner: DeclarationDescriptor,
        modality: Modality,
        visibility: DescriptorVisibility,
        kind: CallableMemberDescriptor.Kind,
        copyOverrides: Boolean
    ): ClassConstructorDescriptor = noImplementation()
}

class KtSymbolBasedAnonymousFunctionDescriptor(
    override val ktSymbol: KtAnonymousFunctionSymbol,
    context: Fe10WrapperContext
) : KtSymbolBasedFunctionLikeDescriptor(context), SimpleFunctionDescriptor {
    override fun getName(): Name = SpecialNames.ANONYMOUS

    override fun getExtensionReceiverParameter(): ReceiverParameterDescriptor? = getExtensionReceiverParameter(ktSymbol)
    override fun getDispatchReceiverParameter(): ReceiverParameterDescriptor? = null
    override fun getContextReceiverParameters(): List<ReceiverParameterDescriptor> = implementationPlanned()

    override fun getPackageFqNameIfTopLevel(): FqName = error("Impossible to be a top-level declaration")

    override fun isOperator(): Boolean = false
    override fun isExternal(): Boolean = false
    override fun isInline(): Boolean = false
    override fun isInfix(): Boolean = false
    override fun isTailrec(): Boolean = false
    override fun isExpect(): Boolean = false
    override fun isActual(): Boolean = false

    override fun getVisibility(): DescriptorVisibility = DescriptorVisibilities.LOCAL
    override fun getModality(): Modality = Modality.FINAL
    override fun getTypeParameters(): List<TypeParameterDescriptor> = emptyList()

    // it doesn't seems like isSuspend are used in FIR for anonymous functions, but it used in FIR2IR so if we really need that
    // we could implement that later
    override fun isSuspend(): Boolean = implementationPostponed()

    override fun getOverriddenDescriptors(): Collection<FunctionDescriptor> = emptyList()

    override fun copy(
        newOwner: DeclarationDescriptor?,
        modality: Modality?,
        visibility: DescriptorVisibility?,
        kind: CallableMemberDescriptor.Kind?,
        copyOverrides: Boolean
    ): SimpleFunctionDescriptor = noImplementation()

    override fun getOriginal(): SimpleFunctionDescriptor = this

    override fun newCopyBuilder(): FunctionDescriptor.CopyBuilder<out SimpleFunctionDescriptor> = noImplementation()
}

private fun KtSymbolBasedDeclarationDescriptor.getDispatchReceiverParameter(ktSymbol: KtCallableSymbol): ReceiverParameterDescriptor? {
    val ktDispatchTypeAndAnnotations = context.withAnalysisSession { ktSymbol.getDispatchReceiverType() } ?: return null
    return KtSymbolStubDispatchReceiverParameterDescriptor(ktDispatchTypeAndAnnotations, context)
}

private fun <T> T.getExtensionReceiverParameter(
    ktSymbol: KtCallableSymbol,
): ReceiverParameterDescriptor? where T : KtSymbolBasedDeclarationDescriptor, T : CallableDescriptor {
    val receiverType = ktSymbol.receiverType ?: return null
    val receiverValue = ExtensionReceiver(this, receiverType.toKotlinType(context), null)
    return Fe10BindingReceiverParameterDescriptorImpl(this, receiverValue, receiverType.getDescriptorsAnnotations(context))
}

private fun KtSymbolBasedDeclarationDescriptor.getTypeParameters(ktSymbol: KtSymbolWithTypeParameters) =
    ktSymbol.typeParameters.map { KtSymbolBasedTypeParameterDescriptor(it, context) }

private class KtSymbolBasedReceiverValue(val ktType: KtType, val context: Fe10WrapperContext) : ReceiverValue {
    override fun getType(): KotlinType = ktType.toKotlinType(context)

    override fun replaceType(newType: KotlinType): ReceiverValue = context.noImplementation("Should be called from IDE")
    override fun getOriginal(): ReceiverValue = this
}

// Don't think that annotation is important here, because containingDeclaration used way more then annotation and they are not supported here
private class KtSymbolStubDispatchReceiverParameterDescriptor(
    val receiverType: KtType,
    val context: Fe10WrapperContext
) : AbstractReceiverParameterDescriptor(Annotations.EMPTY) {
    override fun getContainingDeclaration(): DeclarationDescriptor = context.containerDeclarationImplementationPostponed()

    override fun getValue(): ReceiverValue = KtSymbolBasedReceiverValue(receiverType, context)

    override fun copy(newOwner: DeclarationDescriptor): ReceiverParameterDescriptor =
        context.noImplementation("Copy should be called from IDE code")
}

class KtSymbolBasedValueParameterDescriptor(
    override val ktSymbol: KtValueParameterSymbol,
    context: Fe10WrapperContext,
    val containingDeclaration: KtSymbolBasedFunctionLikeDescriptor,
    index: Int = -1,
) : KtSymbolBasedDeclarationDescriptor(context), KtSymbolBasedNamed, ValueParameterDescriptor {
    override val index: Int

    init {
        if (index != -1) {
            this.index = index
        } else {
            val containerSymbol = containingDeclaration.ktSymbol
            this.index = containerSymbol.valueParameters.indexOf(ktSymbol)
            check(this.index != -1) {
                "Parameter not found in container symbol = $containerSymbol"
            }
        }
    }

    override fun getExtensionReceiverParameter(): ReceiverParameterDescriptor? = null
    override fun getDispatchReceiverParameter(): ReceiverParameterDescriptor? = null
    override fun getContextReceiverParameters(): List<ReceiverParameterDescriptor> = emptyList()

    override fun getTypeParameters(): List<TypeParameterDescriptor> = emptyList()

    override fun getReturnType(): KotlinType = this.type

    override fun getValueParameters(): List<ValueParameterDescriptor> = emptyList()
    override fun hasStableParameterNames(): Boolean = false
    override fun hasSynthesizedParameterNames(): Boolean = false

    override fun <V : Any?> getUserData(key: CallableDescriptor.UserDataKey<V>?): V? = null

    override fun getVisibility(): DescriptorVisibility = DescriptorVisibilities.LOCAL

    // for old FE it should return ArrayType in case of vararg
    override fun getType(): KotlinType {
        val elementType = ktSymbol.returnType.toKotlinType(context)
        if (!ktSymbol.isVararg) return elementType

        // copy from org.jetbrains.kotlin.resolve.DescriptorResolver.getVarargParameterType
        val primitiveArrayType = context.builtIns.getPrimitiveArrayKotlinTypeByPrimitiveKotlinType(elementType)
        return primitiveArrayType ?: context.builtIns.getArrayType(Variance.OUT_VARIANCE, elementType)
    }

    override fun getContainingDeclaration(): CallableDescriptor = containingDeclaration
    override fun getPackageFqNameIfTopLevel(): FqName = error("Couldn't be top-level")

    override fun declaresDefaultValue(): Boolean = ktSymbol.hasDefaultValue

    override val varargElementType: KotlinType?
        get() {
            if (!ktSymbol.isVararg) return null
            return type
        }

    override fun cleanCompileTimeInitializerCache() {}

    override fun getOriginal(): ValueParameterDescriptor = context.incorrectImplementation { this }

    override fun copy(newOwner: CallableDescriptor, newName: Name, newIndex: Int): ValueParameterDescriptor =
        context.noImplementation()

    override fun getOverriddenDescriptors(): Collection<ValueParameterDescriptor> {
        return containingDeclaration.overriddenDescriptors.map { it.valueParameters[index] }
    }

    override val isCrossinline: Boolean
        get() = implementationPostponed()
    override val isNoinline: Boolean
        get() = implementationPostponed()

    override fun isVar(): Boolean = false
    override fun getCompileTimeInitializer(): ConstantValue<*>? = null
    override fun isConst(): Boolean = false

    override fun equals(other: Any?): Boolean {
        if (other === this) return true
        if (other !is KtSymbolBasedValueParameterDescriptor) return false
        return index == other.index && containingDeclaration == other.containingDeclaration
    }

    override fun hashCode(): Int = containingDeclaration.hashCode() * 37 + index

}

abstract class AbstractKtSymbolBasedPropertyDescriptor(
    context: Fe10WrapperContext
) : KtSymbolBasedDeclarationDescriptor(context), KtSymbolBasedNamed, PropertyDescriptor {
    abstract override val ktSymbol: KtVariableSymbol

    override fun getPackageFqNameIfTopLevel(): FqName = (ktSymbol.callableIdIfNonLocal ?: error("should be top-level")).packageName
    override fun getOriginal(): PropertyDescriptor = context.incorrectImplementation { this }


    override fun getExtensionReceiverParameter(): ReceiverParameterDescriptor? = getExtensionReceiverParameter(ktSymbol)
    override fun getDispatchReceiverParameter(): ReceiverParameterDescriptor? = getDispatchReceiverParameter(ktSymbol)

    override fun getContextReceiverParameters(): List<ReceiverParameterDescriptor> = implementationPlanned()

    override fun getTypeParameters(): List<TypeParameterDescriptor> = getTypeParameters(ktSymbol)

    override fun getReturnType(): KotlinType = ktSymbol.returnType.toKotlinType(context)


    override fun getValueParameters(): List<ValueParameterDescriptor> = emptyList()
    override fun hasStableParameterNames(): Boolean = false
    override fun hasSynthesizedParameterNames(): Boolean = false

    override fun getOverriddenDescriptors(): Collection<PropertyDescriptor> {
        val overriddenKtSymbols = context.withAnalysisSession {
            ktSymbol.getAllOverriddenSymbols()
        }

        return overriddenKtSymbols.map { it.toDeclarationDescriptor(context) as PropertyDescriptor }
    }

    override fun <V : Any?> getUserData(key: CallableDescriptor.UserDataKey<V>?): V? = null

    override fun getType(): KotlinType = ktSymbol.returnType.toKotlinType(context)

    override fun isVar(): Boolean = !ktSymbol.isVal

    override fun getAccessors(): List<PropertyAccessorDescriptor> = listOfNotNull(getter, setter)

    override fun cleanCompileTimeInitializerCache() = noImplementation()

    override fun isLateInit(): Boolean = ktSymbol is KtKotlinPropertySymbol && (ktSymbol as KtKotlinPropertySymbol).isLateInit

    override fun getModality(): Modality = when (ktSymbol) {
        is KtJavaFieldSymbol -> (ktSymbol as KtJavaFieldSymbol).modality
        is KtPropertySymbol -> (ktSymbol as KtPropertySymbol).modality
        is KtLocalVariableSymbol -> Modality.FINAL
    }

    override fun isExpect(): Boolean = implementationPostponed()
    override fun isActual(): Boolean = implementationPostponed()
    override fun isExternal(): Boolean = implementationPlanned()

    override fun getInType(): KotlinType = implementationPostponed()

    override fun setOverriddenDescriptors(overriddenDescriptors: MutableCollection<out CallableMemberDescriptor>) =
        noImplementation()

    override fun getKind() = ktSymbol.origin.toCallableDescriptorKind()

    override fun copy(
        newOwner: DeclarationDescriptor?,
        modality: Modality?,
        visibility: DescriptorVisibility?,
        kind: CallableMemberDescriptor.Kind?,
        copyOverrides: Boolean
    ): CallableMemberDescriptor = noImplementation()

    override fun newCopyBuilder(): CallableMemberDescriptor.CopyBuilder<out PropertyDescriptor> = noImplementation()

    override fun isSetterProjectedOut(): Boolean = implementationPostponed()

    override fun getBackingField(): FieldDescriptor = implementationPostponed()
    override fun getDelegateField(): FieldDescriptor = implementationPostponed()
    override val isDelegated: Boolean
        get() = implementationPostponed()
}


class KtSymbolBasedJavaPropertyDescriptor(
    override val ktSymbol: KtJavaFieldSymbol,
    context: Fe10WrapperContext
) : AbstractKtSymbolBasedPropertyDescriptor(context), PropertyDescriptor {
    override fun getVisibility(): DescriptorVisibility = ktSymbol.visibility.toDescriptorVisibility()

    override fun getCompileTimeInitializer(): ConstantValue<*>? = implementationPlanned()
    override fun isConst(): Boolean = implementationPlanned()

    override val getter: PropertyGetterDescriptor?
        get() = null

    override val setter: PropertySetterDescriptor?
        get() = null
}

class KtSymbolBasedPropertyDescriptor(
    override val ktSymbol: KtPropertySymbol,
    context: Fe10WrapperContext
) : AbstractKtSymbolBasedPropertyDescriptor(context), PropertyDescriptor {
    override fun getVisibility(): DescriptorVisibility = ktSymbol.visibility.toDescriptorVisibility()

    override fun getCompileTimeInitializer(): ConstantValue<*>? {
        val constantInitializer = ktSymbol.initializer as? KtConstantInitializerValue ?: return null
        return constantInitializer.constant.toConstantValue()
    }

    override fun isConst(): Boolean = ktSymbol.initializer != null

    override val getter: PropertyGetterDescriptor?
        get() = ktSymbol.getter?.let { KtSymbolBasedPropertyGetterDescriptor(it, this) }

    override val setter: PropertySetterDescriptor?
        get() = ktSymbol.setter?.let { KtSymbolBasedPropertySetterDescriptor(it, this) }
}

class KtSymbolBasedLocalVariableDescriptor(
    override val ktSymbol: KtLocalVariableSymbol,
    context: Fe10WrapperContext
) : AbstractKtSymbolBasedPropertyDescriptor(context), VariableDescriptorWithAccessors {

    override fun getVisibility(): DescriptorVisibility = DescriptorVisibilities.LOCAL

    override fun getCompileTimeInitializer(): ConstantValue<*>? = context.incorrectImplementation { null }

    override fun isConst(): Boolean = false

    override val getter: PropertyGetterDescriptor?
        get() = context.incorrectImplementation { null } // todo: add implementation for delegates

    override val setter: PropertySetterDescriptor?
        get() = context.incorrectImplementation { null } // todo: add implementation for delegates
}

abstract class KtSymbolBasedVariableAccessorDescriptor(
    val propertyDescriptor: KtSymbolBasedPropertyDescriptor
) : KtSymbolBasedDeclarationDescriptor(propertyDescriptor.context), VariableAccessorDescriptor {
    abstract override val ktSymbol: KtPropertyAccessorSymbol

    override fun getPackageFqNameIfTopLevel(): FqName = error("should be called")
    override fun getContainingDeclaration(): DeclarationDescriptor = propertyDescriptor
    override val correspondingVariable: VariableDescriptorWithAccessors
        get() = propertyDescriptor

    override fun getExtensionReceiverParameter(): ReceiverParameterDescriptor? = propertyDescriptor.extensionReceiverParameter
    override fun getDispatchReceiverParameter(): ReceiverParameterDescriptor? = propertyDescriptor.dispatchReceiverParameter
    override fun getContextReceiverParameters(): List<ReceiverParameterDescriptor> = propertyDescriptor.contextReceiverParameters

    override fun getTypeParameters(): List<TypeParameterDescriptor> = emptyList()

    override fun getValueParameters(): List<ValueParameterDescriptor> = emptyList()
    override fun hasStableParameterNames(): Boolean = false
    override fun hasSynthesizedParameterNames(): Boolean = false
    override fun <V : Any?> getUserData(key: CallableDescriptor.UserDataKey<V>?): V? = null

    override fun setOverriddenDescriptors(overriddenDescriptors: MutableCollection<out CallableMemberDescriptor>) =
        noImplementation()

    override fun getKind(): CallableMemberDescriptor.Kind = propertyDescriptor.kind
    override fun getVisibility(): DescriptorVisibility = propertyDescriptor.visibility

    override fun getInitialSignatureDescriptor(): FunctionDescriptor? =
        noImplementation()

    override fun isHiddenToOvercomeSignatureClash(): Boolean = false

    override fun isOperator(): Boolean = false

    override fun isInfix(): Boolean = false

    override fun isInline(): Boolean = ktSymbol.isInline

    override fun isTailrec(): Boolean = false

    override fun isHiddenForResolutionEverywhereBesideSupercalls(): Boolean =
        implementationPostponed()

    override fun isSuspend(): Boolean = false

    override fun newCopyBuilder(): FunctionDescriptor.CopyBuilder<out FunctionDescriptor> =
        noImplementation()

    override fun getModality(): Modality = implementationPlanned()

    override fun isExpect(): Boolean = implementationPostponed()
    override fun isActual(): Boolean = implementationPostponed()
    override fun isExternal(): Boolean = implementationPostponed()
}

class KtSymbolBasedPropertyGetterDescriptor(
    override val ktSymbol: KtPropertyGetterSymbol,
    propertyDescriptor: KtSymbolBasedPropertyDescriptor
) : KtSymbolBasedVariableAccessorDescriptor(propertyDescriptor), PropertyGetterDescriptor {
    override fun getReturnType(): KotlinType = propertyDescriptor.returnType

    override fun getName(): Name = Name.special("<get-${propertyDescriptor.name}>")
    override fun isDefault(): Boolean = ktSymbol.isDefault

    override fun getCorrespondingProperty(): PropertyDescriptor = propertyDescriptor

    override fun copy(
        newOwner: DeclarationDescriptor?,
        modality: Modality?,
        visibility: DescriptorVisibility?,
        kind: CallableMemberDescriptor.Kind?,
        copyOverrides: Boolean
    ): PropertyAccessorDescriptor = noImplementation()

    override fun getOriginal(): PropertyGetterDescriptor = context.incorrectImplementation { this }
    override fun getOverriddenDescriptors(): Collection<PropertyGetterDescriptor> = implementationPostponed()
}

class KtSymbolBasedPropertySetterDescriptor(
    override val ktSymbol: KtPropertySetterSymbol,
    propertyDescriptor: KtSymbolBasedPropertyDescriptor
) : KtSymbolBasedVariableAccessorDescriptor(propertyDescriptor), PropertySetterDescriptor {
    override fun getReturnType(): KotlinType = propertyDescriptor.returnType

    override fun getName(): Name = Name.special("<set-${propertyDescriptor.name}>")
    override fun isDefault(): Boolean = ktSymbol.isDefault

    override fun getCorrespondingProperty(): PropertyDescriptor = propertyDescriptor

    override fun copy(
        newOwner: DeclarationDescriptor?,
        modality: Modality?,
        visibility: DescriptorVisibility?,
        kind: CallableMemberDescriptor.Kind?,
        copyOverrides: Boolean
    ): PropertyAccessorDescriptor = noImplementation()

    override fun getOriginal(): PropertySetterDescriptor = context.incorrectImplementation { this }
    override fun getOverriddenDescriptors(): Collection<PropertySetterDescriptor> = implementationPostponed()
}

class KtSymbolBasedPackageFragmentDescriptor(
    override val fqName: FqName,
    val context: Fe10WrapperContext
) : PackageFragmentDescriptor {

    override fun getName(): Name = fqName.shortName()

    override fun getOriginal(): DeclarationDescriptorWithSource = this

    override fun getSource(): SourceElement = SourceElement.NO_SOURCE

    override val annotations: Annotations
        get() = Annotations.EMPTY

    override fun getContainingDeclaration(): ModuleDescriptor = context.moduleDescriptor

    override fun getMemberScope(): MemberScope = context.noImplementation()

    override fun <R : Any?, D : Any?> accept(visitor: DeclarationDescriptorVisitor<R, D>?, data: D): R =
        context.noImplementation("IDE shouldn't use visitor on descriptors")

    override fun acceptVoid(visitor: DeclarationDescriptorVisitor<Void, Void>?) =
        context.noImplementation("IDE shouldn't use visitor on descriptors")
}

class KtSymbolBasedTypeAliasDescriptor(
    override val ktSymbol: KtTypeAliasSymbol,
    context: Fe10WrapperContext
) : KtSymbolBasedDeclarationDescriptor(context), KtSymbolBasedNamed, TypeAliasDescriptor {
    override val classDescriptor: ClassDescriptor?
        get() = context.implementationPostponed()

    override val constructors: Collection<TypeAliasConstructorDescriptor>
        get() = context.implementationPostponed()

    override val expandedType: SimpleType
        get() = ktSymbol.expandedType.toKotlinType(context, annotations) as SimpleType

    override val underlyingType: SimpleType
        get() = context.implementationPostponed()

    override fun getDeclaredTypeParameters(): List<TypeParameterDescriptor> = context.implementationPostponed()

    override fun getDefaultType(): SimpleType = expandedType

    override fun getModality(): Modality = Modality.FINAL

    override fun getPackageFqNameIfTopLevel(): FqName = (ktSymbol.classIdIfNonLocal ?: error("should be top-level")).packageFqName

    override fun getOriginal(): TypeAliasDescriptor = this

    override fun getTypeConstructor(): TypeConstructor = context.implementationPostponed()

    override fun getVisibility(): DescriptorVisibility = ktSymbol.visibility.toDescriptorVisibility()
    override fun isInner(): Boolean = false

    override fun isActual(): Boolean = context.implementationPostponed()
    override fun isExpect(): Boolean = context.implementationPostponed()
    override fun isExternal(): Boolean = context.implementationPostponed()
}