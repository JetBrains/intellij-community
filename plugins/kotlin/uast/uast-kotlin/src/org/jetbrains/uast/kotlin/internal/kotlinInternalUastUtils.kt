// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.uast.kotlin

import com.intellij.openapi.diagnostic.Attachment
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.psi.*
import com.intellij.psi.impl.cache.TypeInfo
import com.intellij.psi.impl.compiled.ClsTypeElementImpl
import com.intellij.psi.impl.compiled.SignatureParsing
import com.intellij.psi.impl.compiled.StubBuildingVisitor
import com.intellij.psi.util.PsiTypesUtil
import com.intellij.util.SmartList
import org.jetbrains.kotlin.analysis.api.types.KtTypeMappingMode
import org.jetbrains.kotlin.asJava.LightClassUtil
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.builtins.functions.FunctionInvokeDescriptor
import org.jetbrains.kotlin.builtins.isBuiltinFunctionalTypeOrSubtype
import org.jetbrains.kotlin.codegen.signature.BothSignatureWriter
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.descriptors.annotations.AnnotationArgumentVisitor
import org.jetbrains.kotlin.descriptors.annotations.AnnotationDescriptor
import org.jetbrains.kotlin.descriptors.impl.AnonymousFunctionDescriptor
import org.jetbrains.kotlin.descriptors.impl.EnumEntrySyntheticClassDescriptor
import org.jetbrains.kotlin.descriptors.impl.TypeAliasConstructorDescriptor
import org.jetbrains.kotlin.descriptors.synthetic.SyntheticMemberDescriptor
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.core.unwrapIfFakeOverride
import org.jetbrains.kotlin.js.resolve.diagnostics.findPsi
import org.jetbrains.kotlin.load.java.lazy.descriptors.LazyJavaPackageFragment
import org.jetbrains.kotlin.load.java.sam.SamAdapterDescriptor
import org.jetbrains.kotlin.load.kotlin.KotlinJvmBinaryPackageSourceElement
import org.jetbrains.kotlin.load.kotlin.TypeMappingMode
import org.jetbrains.kotlin.load.kotlin.getOptimalModeForReturnType
import org.jetbrains.kotlin.load.kotlin.getOptimalModeForValueParameter
import org.jetbrains.kotlin.metadata.ProtoBuf
import org.jetbrains.kotlin.metadata.deserialization.getExtensionOrNull
import org.jetbrains.kotlin.metadata.jvm.JvmProtoBuf
import org.jetbrains.kotlin.metadata.jvm.deserialization.JvmMemberSignature
import org.jetbrains.kotlin.metadata.jvm.deserialization.JvmProtoBufUtil
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.resolve.*
import org.jetbrains.kotlin.resolve.calls.inference.model.TypeVariableTypeConstructor
import org.jetbrains.kotlin.resolve.calls.model.ArgumentMatch
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall
import org.jetbrains.kotlin.resolve.calls.tower.NewResolvedCallImpl
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.kotlin.resolve.constants.*
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.resolve.references.ReferenceAccess
import org.jetbrains.kotlin.resolve.sam.SamConstructorDescriptor
import org.jetbrains.kotlin.resolve.source.getPsi
import org.jetbrains.kotlin.serialization.deserialization.descriptors.DeserializedCallableMemberDescriptor
import org.jetbrains.kotlin.serialization.deserialization.descriptors.DeserializedClassDescriptor
import org.jetbrains.kotlin.synthetic.SamAdapterExtensionFunctionDescriptor
import org.jetbrains.kotlin.synthetic.SyntheticJavaPropertyDescriptor
import org.jetbrains.kotlin.type.MapPsiToAsmDesc
import org.jetbrains.kotlin.types.*
import org.jetbrains.kotlin.types.typeUtil.builtIns
import org.jetbrains.kotlin.types.typeUtil.contains
import org.jetbrains.kotlin.types.typeUtil.isInterface
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull
import org.jetbrains.uast.*
import org.jetbrains.uast.kotlin.internal.KotlinUastTypeMapper
import org.jetbrains.uast.kotlin.psi.UastFakeDescriptorLightMethod
import org.jetbrains.uast.kotlin.psi.UastFakeSourceLightMethod
import org.jetbrains.uast.kotlin.psi.UastFakeSourceLightPrimaryConstructor
import java.text.StringCharacterIterator

val kotlinUastPlugin: UastLanguagePlugin by lz {
    UastLanguagePlugin.getInstances().find { it.language == KotlinLanguage.INSTANCE }
        ?: KotlinUastLanguagePlugin()
}

internal fun KotlinType.toPsiType(
    source: UElement?,
    element: KtElement,
    config: PsiTypeConversionConfiguration,
): PsiType =
    toPsiType(
        source?.getParentOfType<UDeclaration>(false)?.javaPsi as? PsiModifierListOwner,
        element,
        config
    )

internal fun KotlinType.toPsiType(
    containingLightDeclaration: PsiModifierListOwner?,
    context: KtElement,
    config: PsiTypeConversionConfiguration,
): PsiType {
    if (this.isError) return UastErrorType

    (constructor.declarationDescriptor as? TypeAliasDescriptor)?.let { typeAlias ->
        return typeAlias.expandedType.toPsiType(containingLightDeclaration, context, config)
    }

    if (contains { type -> type.constructor is TypeVariableTypeConstructor }) {
        return UastErrorType
    }

    (constructor.declarationDescriptor as? TypeParameterDescriptor)?.let { typeParameter ->
        (typeParameter.containingDeclaration.toSource()?.getMaybeLightElement() as? PsiTypeParameterListOwner)
            ?.typeParameterList?.typeParameters?.getOrNull(typeParameter.index)
            ?.let { return PsiTypesUtil.getClassType(it) }
        return CommonSupertypes.commonSupertype(typeParameter.upperBounds)
            .toPsiType(containingLightDeclaration, context, config)
    }

    if (arguments.isEmpty()) {
        val typeFqName = this.constructor.declarationDescriptor?.fqNameSafe
        fun PsiPrimitiveType.orBoxed() = if (config.isBoxed) getBoxedType(context) else this
        val psiType = when (typeFqName) {
            StandardClassIds.Int.asSingleFqName() -> PsiTypes.intType().orBoxed()
            StandardClassIds.Long.asSingleFqName() -> PsiTypes.longType().orBoxed()
            StandardClassIds.Short.asSingleFqName() -> PsiTypes.shortType().orBoxed()
            StandardClassIds.Boolean.asSingleFqName() -> PsiTypes.booleanType().orBoxed()
            StandardClassIds.Byte.asSingleFqName() -> PsiTypes.byteType().orBoxed()
            StandardClassIds.Char.asSingleFqName() -> PsiTypes.charType().orBoxed()
            StandardClassIds.Double.asSingleFqName() -> PsiTypes.doubleType().orBoxed()
            StandardClassIds.Float.asSingleFqName() -> PsiTypes.floatType().orBoxed()
            StandardClassIds.Unit.asSingleFqName() -> convertUnitToVoidIfNeeded(context, config.typeOwnerKind, config.isBoxed)
            StandardClassIds.String.asSingleFqName() -> PsiType.getJavaLangString(context.manager, context.resolveScope)
            else -> {
                when (val typeConstructor = this.constructor) {
                    is IntegerValueTypeConstructor ->
                        TypeUtils.getDefaultPrimitiveNumberType(typeConstructor)
                            .toPsiType(containingLightDeclaration, context, config)
                    is IntegerLiteralTypeConstructor ->
                        typeConstructor.getApproximatedType()
                            .toPsiType(containingLightDeclaration, context, config)
                    else -> null
                }
            }
        }
        if (psiType != null) return psiType.annotate(buildAnnotationProvider(this, containingLightDeclaration ?: context))
    }

    if (this.containsLocalTypes()) return UastErrorType

    val project = context.project

    val languageVersionSettings = project.getService(KotlinUastResolveProviderService::class.java)
        .getLanguageVersionSettings(context)

    val signatureWriter = BothSignatureWriter(BothSignatureWriter.Mode.TYPE)

    val approximatedType =
        TypeApproximator(this.builtIns, languageVersionSettings).approximateDeclarationType(this, true)
    val typeMappingMode =
        when (config.typeMappingMode) {
            KtTypeMappingMode.VALUE_PARAMETER -> {
                KotlinUastTypeMapper.typeSystem.getOptimalModeForValueParameter(
                    approximatedType,
                ).mapTypeAliases(TypeMappingMode.GENERIC_ARGUMENT_UAST)
            }
            KtTypeMappingMode.RETURN_TYPE -> {
                KotlinUastTypeMapper.typeSystem.getOptimalModeForReturnType(
                    approximatedType,
                    isAnnotationMethod = context.containingClass()?.isAnnotation() == true,
                ).mapTypeAliases(TypeMappingMode.GENERIC_ARGUMENT_UAST)
            }
            KtTypeMappingMode.GENERIC_ARGUMENT -> TypeMappingMode.GENERIC_ARGUMENT_UAST
            else -> TypeMappingMode.DEFAULT_UAST
        }
    KotlinUastTypeMapper.mapType(approximatedType, signatureWriter, typeMappingMode)

    val signature = StringCharacterIterator(signatureWriter.toString())

    val javaType = SignatureParsing.parseTypeString(signature, StubBuildingVisitor.GUESSING_MAPPER)
    val typeInfo = TypeInfo.fromString(javaType, false)
    val typeText = TypeInfo.createTypeText(typeInfo) ?: return UastErrorType

    val psiTypeParent: PsiElement = containingLightDeclaration ?: context
    if (psiTypeParent.containingFile == null) {
        Logger.getInstance("org.jetbrains.uast.kotlin.KotlinInternalUastUtils")
            .error(
                "initialising ClsTypeElementImpl with null-file parent = $psiTypeParent (of ${psiTypeParent.javaClass}) " +
                        "containing class = ${(psiTypeParent as? PsiMethod)?.containingClass}, " +
                        "containing lightDeclaration = $containingLightDeclaration (of ${containingLightDeclaration?.javaClass}), " +
                        "context = $context (of ${context.javaClass})"
            )
    }
    return ClsTypeElementImpl(psiTypeParent, typeText, '\u0000').type
}

private fun renderAnnotation(annotation: AnnotationDescriptor): String? {
    val fqn = annotation.fqName?.asString() ?: return null
    val valueArguments = annotation.allValueArguments
    val valuesList = SmartList<String>().apply {
        for ((k, v) in valueArguments.entries) {
            add("${k.identifier} = ${renderConstantValue(v)}")
        }
    }
    return "@$fqn(${valuesList.joinToString(", ")})"
}

private fun renderConstantValue(value: ConstantValue<*>?): String? = value?.accept(object : AnnotationArgumentVisitor<String?, String?> {
    override fun visitLongValue(value: LongValue, data: String?): String = value.value.toString()
    override fun visitIntValue(value: IntValue, data: String?): String = value.value.toString()
    override fun visitErrorValue(value: ErrorValue?, data: String?): String? = null
    override fun visitShortValue(value: ShortValue, data: String?): String = value.value.toString()
    override fun visitByteValue(value: ByteValue, data: String?): String = value.value.toString()
    override fun visitDoubleValue(value: DoubleValue, data: String?): String = value.value.toString()
    override fun visitFloatValue(value: FloatValue, data: String?): String = value.value.toString()
    override fun visitBooleanValue(value: BooleanValue, data: String?): String = value.value.toString()
    override fun visitCharValue(value: CharValue, data: String?): String = "'${value.value}'"
    override fun visitStringValue(value: StringValue, data: String?): String = "\"${value.value}\""
    override fun visitNullValue(value: NullValue?, data: String?): String = "null"
    override fun visitEnumValue(value: EnumValue, data: String?): String =
        value.value.run { first.asSingleFqName().asString() + "." + second.asString() }

    override fun visitArrayValue(value: ArrayValue, data: String?): String =
        value.value.mapNotNull { renderConstantValue(it) }.joinToString(", ", "{", "}")

    override fun visitAnnotationValue(value: AnnotationValue, data: String?): String? = renderAnnotation(value.value)
    override fun visitKClassValue(value: KClassValue, data: String?): String = value.value.toString() + ".class"
    override fun visitUByteValue(value: UByteValue, data: String?): String = value.value.toString()
    override fun visitUShortValue(value: UShortValue, data: String?): String = value.value.toString()
    override fun visitUIntValue(value: UIntValue, data: String?): String = value.value.toString()
    override fun visitULongValue(value: ULongValue, data: String?): String = value.value.toString()
}, null)

private fun buildAnnotationProvider(ktType: KotlinType, context: PsiElement): TypeAnnotationProvider {
    val result = SmartList<PsiAnnotation>()
    val psiElementFactory = PsiElementFactory.getInstance(context.project)
    for (annotation in ktType.annotations) {
        val annotationText = renderAnnotation(annotation) ?: continue
        try {
            result.add(psiElementFactory.createAnnotationFromText(annotationText, context))
        } catch (e: Exception) {
            if (e is ControlFlowException) throw e
            Logger.getInstance("org.jetbrains.uast.kotlin.KotlinInternalUastUtils")
                .error("failed to create annotation from text", e, Attachment("annotationText.txt", annotationText))
        }
    }
    if (result.isEmpty()) return TypeAnnotationProvider.EMPTY
    return TypeAnnotationProvider.Static.create(result.toArray(PsiAnnotation.EMPTY_ARRAY))
}

internal fun KtTypeReference?.toPsiType(source: UElement, boxed: Boolean = false): PsiType {
    if (this == null) return UastErrorType
    return (getType() ?: return UastErrorType)
        .toPsiType(source, this, PsiTypeConversionConfiguration.create(this, boxed))
}

internal fun KtElement.analyze(): BindingContext {
    if (!canAnalyze()) return BindingContext.EMPTY
    return project.getService(KotlinUastResolveProviderService::class.java)
        ?.getBindingContextIfAny(this) ?: BindingContext.EMPTY
}

internal fun KtExpression.getExpectedType(): KotlinType? = analyze()[BindingContext.EXPECTED_EXPRESSION_TYPE, this]

internal fun KtTypeReference.getType(): KotlinType? =
    analyze()[BindingContext.TYPE, this]
        ?: getTypeAsTypeArgument()

private fun KtTypeReference.getTypeAsTypeArgument(): KotlinType? {
    val call = getParentOfType<KtCallElement>(strict = true) ?: return null
    val resolvedCall = call.getResolvedCall(analyze()) ?: return null
    val typeProjection = call.typeArguments.find { it.typeReference == this } ?: return null
    val index = call.typeArguments.indexOf(typeProjection)
    val paramDescriptor = resolvedCall.candidateDescriptor.typeParameters.find { it.index == index } ?: return null
    return resolvedCall.typeArguments[paramDescriptor]
}

internal fun KtCallableDeclaration.getReturnType(): KotlinType? =
    (analyze()[BindingContext.DECLARATION_TO_DESCRIPTOR, this] as? CallableDescriptor)?.returnType

internal fun KotlinType.getFunctionalInterfaceType(
    source: UElement,
    element: KtElement,
    typeOwnerKind: TypeOwnerKind,
): PsiType? =
    takeIf { it.isInterface() && !it.isBuiltinFunctionalTypeOrSubtype }
        ?.toPsiType(source, element, PsiTypeConversionConfiguration(typeOwnerKind))

internal fun KotlinULambdaExpression.getFunctionalInterfaceType(): PsiType? {
    val parent = if (sourcePsi.parent is KtLabeledExpression) {
        // lambda -> labeled expression -> lambda argument (value argument)
        sourcePsi.parent.parent
    } else {
        sourcePsi.parent
    }
    if (parent is KtBinaryExpressionWithTypeRHS)
        return parent.right?.getType()?.getFunctionalInterfaceType(this, sourcePsi, parent.right!!.typeOwnerKind)
    if (parent is KtValueArgument) run {
        val callExpression = parent.parents.take(2).firstIsInstanceOrNull<KtCallExpression>() ?: return@run
        val resolvedCall = callExpression.getResolvedCall(callExpression.analyze()) ?: return@run

        // NewResolvedCallImpl can be used as a marker meaning that this code is working under *new* inference
        if (resolvedCall is NewResolvedCallImpl) {
            val samConvertedArgument = resolvedCall.getExpectedTypeForSamConvertedArgument(parent)

            // Same as if in old inference we would get SamDescriptor
            if (samConvertedArgument != null) {
                val type = getTypeByArgument(resolvedCall, parent) ?: return@run
                return type.getFunctionalInterfaceType(this, sourcePsi, callExpression.typeOwnerKind)
            }
        }

        val candidateDescriptor = resolvedCall.candidateDescriptor as? SyntheticMemberDescriptor<*> ?: return@run
        when (candidateDescriptor) {
            is SamConstructorDescriptor ->
                return candidateDescriptor.returnType?.getFunctionalInterfaceType(this, sourcePsi, callExpression.typeOwnerKind)
            is SamAdapterDescriptor<*>, is SamAdapterExtensionFunctionDescriptor -> {
                val type = getTypeByArgument(resolvedCall, parent) ?: return@run
                return type.getFunctionalInterfaceType(this, sourcePsi, callExpression.typeOwnerKind)
            }
        }
    }
    return sourcePsi.getExpectedType()?.getFunctionalInterfaceType(this, sourcePsi, sourcePsi.typeOwnerKind)
}

internal fun resolveToPsiMethod(ktElement: KtElement): PsiMethod? {
    val context = ktElement.analyze()
    if (ktElement is KtArrayAccessExpression) {
        // Try getter first, e.g., array[...] += v, ... = array[...], or ...(..., array[...], ...)
        context[BindingContext.INDEXED_LVALUE_GET, ktElement]?.resultingDescriptor
            ?.let { resolveToPsiMethod(ktElement, it) }
            ?.let { return it }
        // Then, setter, e.g., array[...] = v
        context[BindingContext.INDEXED_LVALUE_SET, ktElement]?.resultingDescriptor
            ?.let { resolveToPsiMethod(ktElement, it) }
            ?.let { return it }
    }
    return ktElement.getResolvedCall(context)?.resultingDescriptor?.let { resolveToPsiMethod(ktElement, it) }
}

internal fun resolveToPsiMethod(
    context: KtElement,
    descriptor: DeclarationDescriptor,
    source: PsiElement? = descriptor.toSource()
): PsiMethod? {

    if (descriptor is TypeAliasConstructorDescriptor) {
        return resolveToPsiMethod(context, descriptor.underlyingConstructorDescriptor)
    }

    // import pkg.to.Object.member
    if (descriptor is FunctionImportedFromObject) {
        return resolveToPsiMethod(context, descriptor.callableFromObject)
    }

    // For synthetic members in enum classes, `source` points to their containing enum class.
    if (source is KtClass && source.isEnum() && descriptor is SimpleFunctionDescriptor) {
        val lightClass = source.toLightClass() ?: return null
        lightClass.methods.find { it.name == descriptor.name.identifier }?.let { return it }
    }

    // Default primary constructor
    if (descriptor is ConstructorDescriptor && descriptor.isPrimary
        && source is KtClassOrObject && source.primaryConstructor == null
        && source.secondaryConstructors.isEmpty()
    ) {
        val lightClass = source.toLightClass() ?: return null
        lightClass.constructors.firstOrNull()?.let { return it }
        if (source.isLocal) {
            return UastFakeSourceLightPrimaryConstructor(source, lightClass)
        }
        return null
    }

    // FunctionN::invoke
    if (descriptor is FunctionInvokeDescriptor) {
        return resolveToPsiClass({ null }, descriptor.containingDeclaration, context)
            ?.methods
            ?.singleOrNull() // FunctionN is SAM!
    }

    return when (source) {
        is KtFunction ->
            if (source.isLocal)
                getContainingLightClass(source)?.let { UastFakeSourceLightMethod(source, it) }
            else // UltraLightMembersCreator.createMethods() returns nothing for JVM-invisible methods, so fake it if we get null here
                LightClassUtil.getLightClassMethod(source) ?: getContainingLightClass(source)?.let { UastFakeSourceLightMethod(source, it) }
        is PsiMethod -> source
        null -> {
            val unwrapped = descriptor.unwrapIfFakeOverride() as? DeserializedCallableMemberDescriptor ?: descriptor
            resolveDeserialized(context, unwrapped) as? PsiMethod
        }
        else -> null
    }
}

internal fun resolveToClassIfConstructorCallImpl(ktCallElement: KtCallElement, source: UElement): PsiElement? =
    when (val resultingDescriptor = ktCallElement.getResolvedCall(ktCallElement.analyze())?.descriptorForResolveViaConstructor()) {
        is ConstructorDescriptor -> {
            ktCallElement.calleeExpression?.let { resolveToDeclarationImpl(it, resultingDescriptor.constructedClass) }
        }
        is SamConstructorDescriptor -> {
            (resultingDescriptor.returnType
                ?.getFunctionalInterfaceType(source, ktCallElement, ktCallElement.typeOwnerKind) as? PsiClassType)?.resolve()
        }
        else -> null
    }

// In new inference, SAM constructor is substituted with a function descriptor, so we use candidate descriptor to preserve behavior
private fun ResolvedCall<*>.descriptorForResolveViaConstructor(): CallableDescriptor? {
    return if (this is NewResolvedCallImpl) candidateDescriptor else resultingDescriptor
}

internal fun resolveToDeclarationImpl(sourcePsi: KtExpression): PsiElement? =
    when (sourcePsi) {
        is KtSimpleNameExpression ->
            sourcePsi.analyze()[BindingContext.REFERENCE_TARGET, sourcePsi]
                ?.let { resolveToDeclarationImpl(sourcePsi, it) }
        else ->
            sourcePsi.getResolvedCall(sourcePsi.analyze())?.resultingDescriptor
                ?.let { descriptor -> resolveToDeclarationImpl(sourcePsi, descriptor) }
    }

fun resolveToDeclarationImpl(sourcePsi: KtExpression, declarationDescriptor: DeclarationDescriptor): PsiElement? {
    declarationDescriptor.toSource()?.getMaybeLightElement(sourcePsi)?.let { return it }

    @Suppress("NAME_SHADOWING")
    var declarationDescriptor = declarationDescriptor
    if (declarationDescriptor is ImportedFromObjectCallableDescriptor<*>) {
        declarationDescriptor = declarationDescriptor.callableFromObject
    }
    if (declarationDescriptor is SyntheticJavaPropertyDescriptor) {
        declarationDescriptor = when (sourcePsi.readWriteAccess()) {
            ReferenceAccess.WRITE, ReferenceAccess.READ_WRITE ->
                declarationDescriptor.setMethod ?: declarationDescriptor.getMethod
            ReferenceAccess.READ -> declarationDescriptor.getMethod
        }
    }

    if (declarationDescriptor is PackageViewDescriptor) {
        return JavaPsiFacade.getInstance(sourcePsi.project).findPackage(declarationDescriptor.fqName.asString())
    }

    resolveToPsiClass({ sourcePsi.toUElement() }, declarationDescriptor, sourcePsi)?.let {
        return if (declarationDescriptor is EnumEntrySyntheticClassDescriptor) {
            // An enum entry, a subtype of enum class, is resolved to the enclosing enum class if the mapped type is used.
            // However, the expected resolution result is literally the enum entry, not the enum class.
            // From the resolved enum class (as PsiClass), we can search for the enum entry (as PsiField).
            it.findFieldByName(declarationDescriptor.name.asString(), false)
        } else it
    }

    if (declarationDescriptor is DeclarationDescriptorWithSource) {
        declarationDescriptor.source.getPsi()?.takeIf { it.isValid }?.let { it.getMaybeLightElement() ?: it }?.let { return it }
    }

    if (declarationDescriptor is ValueParameterDescriptor) {
        val parentDeclaration = resolveToDeclarationImpl(sourcePsi, declarationDescriptor.containingDeclaration)
        if (parentDeclaration is PsiClass && parentDeclaration.isAnnotationType) {
            parentDeclaration.findMethodsByName(declarationDescriptor.name.asString(), false).firstOrNull()?.let { return it }
        }
        // Implicit lambda parameter `it`
        if (declarationDescriptor.isImplicitLambdaParameter(sourcePsi)) {
            // From its containing lambda (of function literal), build ULambdaExpression
            val lambda = declarationDescriptor.containingDeclaration.findPsi().toUElementOfType<ULambdaExpression>()
            // and return javaPsi of the corresponding lambda implicit parameter
            return lambda?.valueParameters?.singleOrNull()?.javaPsi
        }
    }

    if (declarationDescriptor is CallableMemberDescriptor && declarationDescriptor.kind == CallableMemberDescriptor.Kind.FAKE_OVERRIDE) {
        declarationDescriptor.overriddenDescriptors.asSequence()
            .mapNotNull { resolveToDeclarationImpl(sourcePsi, it) }
            .firstOrNull()
            ?.let { return it }
    }

    resolveDeserialized(sourcePsi, declarationDescriptor, sourcePsi.readWriteAccess())?.let { return it }

    return null
}

private fun ValueParameterDescriptor.isImplicitLambdaParameter(sourcePsi: KtExpression): Boolean {
    return containingDeclaration is AnonymousFunctionDescriptor &&
            name.identifierOrNullIfSpecial == "it" &&
            // Implicit lambda parameter doesn't have a source PSI.
            source.getPsi() == null &&
            // But, that could be the case for a declaration from Library. Double-check the slice in the binding context
            sourcePsi.analyze().get(BindingContext.AUTO_CREATED_IT, this) != null
}

private fun resolveContainingDeserializedClass(context: KtElement, memberDescriptor: DeserializedCallableMemberDescriptor): PsiClass? {
    return when (val containingDeclaration = memberDescriptor.containingDeclaration) {
        is LazyJavaPackageFragment -> {
            val binaryPackageSourceElement = containingDeclaration.source as? KotlinJvmBinaryPackageSourceElement ?: return null
            val containingBinaryClass = binaryPackageSourceElement.getContainingBinaryClass(memberDescriptor) ?: return null
            val containingClassQualifiedName = containingBinaryClass.classId.asSingleFqName().asString()
            JavaPsiFacade.getInstance(context.project).findClass(containingClassQualifiedName, context.resolveScope) ?: return null
        }
        is DeserializedClassDescriptor -> {
            val declaredPsiType = containingDeclaration.defaultType.toPsiType(
                null as PsiModifierListOwner?,
                context,
                PsiTypeConversionConfiguration(TypeOwnerKind.DECLARATION)
            )
            (declaredPsiType as? PsiClassType)?.resolve() ?: return null
        }
        else -> return null
    }
}

private fun resolveToPsiClass(uElement: () -> UElement?, declarationDescriptor: DeclarationDescriptor, context: KtElement): PsiClass? =
    when (declarationDescriptor) {
        is ConstructorDescriptor -> declarationDescriptor.returnType
        is ClassDescriptor -> declarationDescriptor.defaultType
        is TypeParameterDescriptor -> declarationDescriptor.defaultType
        is TypeAliasDescriptor -> declarationDescriptor.expandedType
        else -> null
    }?.toPsiType(
        uElement.invoke(),
        context,
        PsiTypeConversionConfiguration(TypeOwnerKind.DECLARATION, isBoxed = true)
    ).let { PsiTypesUtil.getPsiClass(it) }

private fun DeclarationDescriptor.toSource(): PsiElement? {
    return try {
        DescriptorToSourceUtils.getEffectiveReferencedDescriptors(this)
            .asSequence()
            .mapNotNull { DescriptorToSourceUtils.getSourceFromDescriptor(it) }
            .find { it.isValid }
    } catch (e: ProcessCanceledException) {
        throw e
    } catch (e: Exception) {
        Logger.getInstance("DeclarationDescriptor.toSource").error(e)
        null
    }
}

private fun resolveDeserialized(
    context: KtElement,
    descriptor: DeclarationDescriptor,
    accessHint: ReferenceAccess? = null
): PsiModifierListOwner? {
    if (descriptor !is DeserializedCallableMemberDescriptor) return null

    val psiClass = resolveContainingDeserializedClass(context, descriptor) ?: return null

    val proto = descriptor.proto
    val nameResolver = descriptor.nameResolver
    val typeTable = descriptor.typeTable

    return when (proto) {
        is ProtoBuf.Function -> {
            psiClass.getMethodBySignature(
                JvmProtoBufUtil.getJvmMethodSignature(proto, nameResolver, typeTable)
                    ?: getMethodSignatureFromDescriptor(context, descriptor)
            ) ?: UastFakeDescriptorLightMethod(descriptor as SimpleFunctionDescriptor, psiClass, context) // fake Java-invisible methods
        }
        is ProtoBuf.Constructor -> {
            val signature = JvmProtoBufUtil.getJvmConstructorSignature(proto, nameResolver, typeTable)
                ?: getMethodSignatureFromDescriptor(context, descriptor)
                ?: return null

            psiClass.constructors.firstOrNull { it.matchesDesc(signature.desc) }
        }
        is ProtoBuf.Property -> {
            JvmProtoBufUtil.getJvmFieldSignature(proto, nameResolver, typeTable, false)
                ?.let { signature -> psiClass.fields.firstOrNull { it.name == signature.name } }
                ?.let { return it }

            val propertySignature = proto.getExtensionOrNull(JvmProtoBuf.propertySignature)
            if (propertySignature != null) {
                with(propertySignature) {
                    when {
                        hasSetter() && accessHint?.isWrite == true -> setter // treat += etc. as write as was done elsewhere
                        hasGetter() && accessHint?.isRead != false -> getter
                        else -> null // it should have been handled by the previous case
                    }
                }?.let { methodSignature ->
                    psiClass.getMethodBySignature(
                        nameResolver.getString(methodSignature.name),
                        if (methodSignature.hasDesc()) nameResolver.getString(methodSignature.desc) else null
                    )
                }?.let { return it }

            } else if (proto.hasName()) {
                // Property without a Property signature, looks like a @JvmField
                val name = nameResolver.getString(proto.name)
                psiClass.fields
                    .firstOrNull { it.name == name }
                    ?.let { return it }
            }

            getMethodSignatureFromDescriptor(context, descriptor)
                ?.let { signature -> psiClass.getMethodBySignature(signature) }
                ?.let { return it }
        }
        else -> null
    }
}

private fun PsiClass.getMethodBySignature(methodSignature: JvmMemberSignature?) = methodSignature?.let { signature ->
    getMethodBySignature(signature.name, signature.desc)
}

private fun PsiClass.getMethodBySignature(name: String, descr: String?) =
    methods.firstOrNull { method -> method.name == name && descr?.let { method.matchesDesc(it) } ?: true }

private fun PsiMethod.matchesDesc(desc: String) = desc == this.desc

private fun getMethodSignatureFromDescriptor(context: KtElement, descriptor: CallableDescriptor): JvmMemberSignature? {
    fun PsiType.raw() = (this as? PsiClassType)?.rawType() ?: PsiPrimitiveType.getUnboxedType(this) ?: this
    fun KotlinType.toPsiType() =
        toPsiType(null as PsiModifierListOwner?, context, PsiTypeConversionConfiguration(TypeOwnerKind.DECLARATION)).raw()

    val originalDescriptor = descriptor.original
    val receiverType = originalDescriptor.extensionReceiverParameter?.type?.toPsiType()
    val parameterTypes = listOfNotNull(receiverType) + originalDescriptor.valueParameters.map { it.type.toPsiType() }
    val returnType = originalDescriptor.returnType?.toPsiType() ?: PsiTypes.voidType()

    if (parameterTypes.any { !it.isValid } || !returnType.isValid) {
        return null
    }

    val desc = parameterTypes.joinToString("", prefix = "(", postfix = ")") { MapPsiToAsmDesc.typeDesc(it) } +
            MapPsiToAsmDesc.typeDesc(returnType)

    return JvmMemberSignature.Method(descriptor.name.asString(), desc)
}

private fun KotlinType.containsLocalTypes(visited: MutableSet<KotlinType> = hashSetOf()): Boolean {
    if (!visited.add(this)) return false
    val typeDeclarationDescriptor = this.constructor.declarationDescriptor
    if (typeDeclarationDescriptor is ClassDescriptor && DescriptorUtils.isLocal(typeDeclarationDescriptor)) {
        return true
    }

    return arguments.any { !it.isStarProjection && it.type.containsLocalTypes(visited) }
            || constructor.supertypes.any { it.containsLocalTypes(visited) }
}

private fun getTypeByArgument(
    resolvedCall: ResolvedCall<*>,
    argument: ValueArgument
): KotlinType? {
    val parameterInfo = (resolvedCall.getArgumentMapping(argument) as? ArgumentMatch)?.valueParameter ?: return null
    return parameterInfo.type
}
