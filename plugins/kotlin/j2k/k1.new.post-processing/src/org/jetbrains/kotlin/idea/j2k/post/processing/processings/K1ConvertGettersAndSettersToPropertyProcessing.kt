// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("DuplicatedCode", "DEPRECATION")

package org.jetbrains.kotlin.idea.j2k.post.processing.processings

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.OverridingMethodsSearch
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.parentOfType
import com.intellij.refactoring.rename.RenamePsiElementProcessor
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.asJava.toLightMethods
import org.jetbrains.kotlin.caches.resolve.KotlinCacheService
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.VariableDescriptor
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget.PROPERTY_GETTER
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget.PROPERTY_SETTER
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.util.or
import org.jetbrains.kotlin.idea.base.util.projectScope
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.codeinsight.utils.isRedundantGetter
import org.jetbrains.kotlin.idea.codeinsight.utils.isRedundantSetter
import org.jetbrains.kotlin.idea.codeinsight.utils.removeRedundantGetter
import org.jetbrains.kotlin.idea.codeinsight.utils.removeRedundantSetter
import org.jetbrains.kotlin.idea.core.setVisibility
import org.jetbrains.kotlin.idea.intentions.addUseSiteTarget
import org.jetbrains.kotlin.idea.quickfix.AddAnnotationTargetFix.Companion.getExistingAnnotationTargets
import org.jetbrains.kotlin.idea.refactoring.isAbstract
import org.jetbrains.kotlin.idea.refactoring.isInterfaceClass
import org.jetbrains.kotlin.idea.references.KtSimpleNameReference
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.references.readWriteAccess
import org.jetbrains.kotlin.idea.resolve.ResolutionFacade
import org.jetbrains.kotlin.idea.search.usagesSearch.descriptor
import org.jetbrains.kotlin.idea.util.CommentSaver
import org.jetbrains.kotlin.idea.util.IdeDescriptorRenderers
import org.jetbrains.kotlin.j2k.*
import org.jetbrains.kotlin.js.resolve.diagnostics.findPsi
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.lexer.KtTokens.*
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.nj2k.*
import org.jetbrains.kotlin.nj2k.externalCodeProcessing.JKFakeFieldData
import org.jetbrains.kotlin.nj2k.externalCodeProcessing.JKFieldData
import org.jetbrains.kotlin.nj2k.externalCodeProcessing.JKPhysicalMethodData
import org.jetbrains.kotlin.nj2k.externalCodeProcessing.NewExternalCodeProcessing
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*
import org.jetbrains.kotlin.resolve.descriptorUtil.annotationClass
import org.jetbrains.kotlin.resolve.descriptorUtil.getSuperClassNotAny
import org.jetbrains.kotlin.resolve.descriptorUtil.getSuperInterfaces
import org.jetbrains.kotlin.resolve.sam.getSingleAbstractMethodOrNull
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter
import org.jetbrains.kotlin.resolve.scopes.getDescriptorsFiltered
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.isError
import org.jetbrains.kotlin.types.typeUtil.isSubtypeOf
import org.jetbrains.kotlin.types.typeUtil.isUnit
import org.jetbrains.kotlin.types.typeUtil.makeNullable
import org.jetbrains.kotlin.util.capitalizeDecapitalize.decapitalizeAsciiOnly
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import org.jetbrains.kotlin.utils.mapToIndex

/**
 * This processing tries to convert functions that look like getters and setters
 * into a single property, taking into account various complicated rules
 * of what makes a legal Kotlin property (for example, regarding inheritance)
 */
internal class K1ConvertGettersAndSettersToPropertyProcessing : ElementsBasedPostProcessing() {
    override val options: PostProcessingOptions =
        PostProcessingOptions(
            disablePostprocessingFormatting = false // without it comment saver will make the file invalid :(
        )

    @OptIn(KaAllowAnalysisOnEdt::class)
    override fun runProcessing(elements: List<PsiElement>, converterContext: NewJ2kConverterContext) {
        val ktElements = elements.filterIsInstance<KtElement>().ifEmpty { return }
        val psiFactory = KtPsiFactory(converterContext.project)
        val searcher = JKInMemoryFilesSearcher.create(ktElements)
        val resolutionFacade = runReadAction {
            KotlinCacheService.getInstance(converterContext.project).getResolutionFacade(ktElements)
        }

        val collector = PropertiesDataCollector(resolutionFacade, searcher)
        val filter = PropertiesDataFilter(resolutionFacade, ktElements, searcher, psiFactory)
        val externalProcessingUpdater = ExternalProcessingUpdater(converterContext.externalCodeProcessor)
        val converter = ClassConverter(searcher, psiFactory)

        val classesWithPropertiesData: List<Pair<KtClassOrObject, List<PropertyData>>> = runReadAction {
            val classes = ktElements.descendantsOfType<KtClassOrObject>().sortedByInheritance(resolutionFacade)
            classes.map { klass -> klass to collector.collectPropertiesData(klass) }
        }.ifEmpty { return }

        for ((klass, propertiesData) in classesWithPropertiesData) {
            val propertiesWithAccessors = runReadAction { filter.filter(klass, propertiesData) }

            allowAnalysisOnEdt {
                runReadAction {
                    analyze(klass) {
                        externalProcessingUpdater.update(klass, propertiesWithAccessors)
                    }
                }
            }

            runUndoTransparentActionInEdt(inWriteAction = true) {
                converter.convertClass(klass, propertiesWithAccessors)
            }
        }
    }

    context(KaSession)
    override fun computeApplier(elements: List<PsiElement>, converterContext: NewJ2kConverterContext): PostProcessingApplier {
        error("Not supported in K1 J2K")
    }

    private fun List<KtClassOrObject>.sortedByInheritance(resolutionFacade: ResolutionFacade): List<KtClassOrObject> {
        val sorted = mutableListOf<KtClassOrObject>()
        val visited = Array(size) { false }
        val descriptors = map { it.resolveToDescriptorIfAny(resolutionFacade)!! }
        val descriptorToIndex = descriptors.mapToIndex()
        val outers = descriptors.map { descriptor ->
            descriptor.superClassAndSuperInterfaces().mapNotNull { descriptorToIndex[it] }
        }

        fun dfs(current: Int) {
            visited[current] = true
            for (outer in outers[current]) {
                if (!visited[outer]) {
                    dfs(outer)
                }
            }
            sorted.add(get(current))
        }

        for (index in descriptors.indices) {
            if (!visited[index]) {
                dfs(index)
            }
        }
        return sorted
    }
}

/**
 * Extracts groups of (getter, setter, property) for a class
 * that are potentially eligible to be converted to a single property
 */
private class PropertiesDataCollector(private val resolutionFacade: ResolutionFacade, private val searcher: JKInMemoryFilesSearcher) {
    private val propertyNameToSuperType: MutableMap<Pair<KtClassOrObject, String>, KotlinType> = mutableMapOf()

    fun collectPropertiesData(klass: KtClassOrObject): List<PropertyData> {
        val propertyInfos: List<PropertyInfo> = klass.declarations.mapNotNull { it.asPropertyInfo() }
        val propertyInfoGroups: Collection<List<PropertyInfo>> =
            propertyInfos.groupBy { it.name.removePrefix("is").decapitalizeAsciiOnly() }.values

        return propertyInfoGroups.mapNotNull { group -> collectPropertyData(klass, group) }
    }

    private fun collectPropertyData(klass: KtClassOrObject, propertyInfoGroup: List<PropertyInfo>): PropertyData? {
        val property = propertyInfoGroup.firstIsInstanceOrNull<RealProperty>()
        val getter = propertyInfoGroup.firstIsInstanceOrNull<RealGetter>()
        val setter = propertyInfoGroup.firstIsInstanceOrNull<RealSetter>()?.takeIf { setterCandidate ->
            if (getter == null) return@takeIf true
            val getterType = getter.function.type() ?: return@takeIf false
            val setterType = setterCandidate.function.valueParameters.first().type() ?: return@takeIf false
            // The inferred nullability of accessors may be different due to semi-random reasons,
            // so we check types compatibility ignoring nullability. Anyway, the final property type will be nullable if necessary.
            getterType.isSubtypeOf(setterType.makeNullable())
        }

        val accessor = getter ?: setter ?: return null
        val name = accessor.name
        val functionDescriptor = accessor.function.resolveToDescriptorIfAny(resolutionFacade) ?: return null

        // Fun interfaces cannot have abstract properties
        if (klass.isSamDescriptor(functionDescriptor)) return null

        val superDeclarationOwner = functionDescriptor.getSuperDeclarationOwner()
        val type = propertyNameToSuperType[superDeclarationOwner to name]
            ?: calculatePropertyType(getter?.function, setter?.function)
            ?: return null
        propertyNameToSuperType[klass to name] = type

        return PropertyData(
            property,
            getter?.withTargetSet(property),
            setter?.withTargetSet(property),
            type
        )
    }

    private fun KtDeclaration.asPropertyInfo(): PropertyInfo? {
        return when (this) {
            is KtProperty -> RealProperty(this, name ?: return null)
            is KtNamedFunction -> asGetter() ?: asSetter()
            else -> null
        }
    }

    private fun KtNamedFunction.asGetter(): Getter? {
        val name = name?.asGetterName() ?: return null
        if (valueParameters.isNotEmpty()) return null
        if (typeParameters.isNotEmpty()) return null

        val returnExpression = bodyExpression?.statements()?.singleOrNull() as? KtReturnExpression
        val property = returnExpression?.returnedExpression?.unpackedReferenceToProperty()
        val getterType = type()
        val singleTimeUsedTarget = property?.takeIf {
            it.containingClass() == containingClass() && getterType != null && it.type() == getterType
        }

        return RealGetter(this, singleTimeUsedTarget, name, singleTimeUsedTarget != null)
    }

    private fun KtNamedFunction.asSetter(): Setter? {
        val name = name?.asSetterName() ?: return null
        val parameter = valueParameters.singleOrNull() ?: return null
        if (typeParameters.isNotEmpty()) return null
        if (resolveToDescriptorIfAny(resolutionFacade)?.returnType?.isUnit() != true) return null

        val binaryExpression = bodyExpression?.statements()?.singleOrNull() as? KtBinaryExpression
        val property = binaryExpression?.let { expression ->
            if (expression.operationToken != EQ) return@let null
            val right = expression.right as? KtNameReferenceExpression ?: return@let null
            if (right.resolve() != parameter) return@let null
            expression.left?.unpackedReferenceToProperty()
        }
        val singleTimeUsedTarget = property?.takeIf {
            it.containingClass() == containingClass() && it.type() == parameter.type()
        }

        return RealSetter(this, singleTimeUsedTarget, name, singleTimeUsedTarget != null)
    }

    private fun KtExpression.statements(): List<KtExpression> =
        if (this is KtBlockExpression) statements else listOf(this)

    private inline fun <reified A : RealAccessor> A.withTargetSet(property: RealProperty?): A = when {
        property == null -> this
        target != null -> this
        property.property.usages(searcher, scope = function).any() -> updateTarget(property.property) as A
        else -> this
    }

    private fun KtClassOrObject.isSamDescriptor(functionDescriptor: FunctionDescriptor): Boolean {
        val classDescriptor = descriptor as? ClassDescriptor ?: return false
        return getSingleAbstractMethodOrNull(classDescriptor) == functionDescriptor
    }

    private fun FunctionDescriptor.getSuperDeclarationOwner(): KtClassOrObject? {
        val overriddenDeclaration = overriddenDescriptors.firstOrNull()?.findPsi() as? KtDeclaration
        return overriddenDeclaration?.containingClassOrObject
    }

    private fun calculatePropertyType(getter: KtNamedFunction?, setter: KtNamedFunction?): KotlinType? {
        val getterType = getter?.resolveToDescriptorIfAny(resolutionFacade)?.returnType
        val setterType = setter?.resolveToDescriptorIfAny(resolutionFacade)?.valueParameters?.singleOrNull()?.type
        return when {
            getterType != null && getterType.isMarkedNullable -> getterType
            setterType != null && setterType.isMarkedNullable -> setterType
            else -> getterType ?: setterType
        }
    }
}

/**
 * Filters the accessors that are eligible for conversion to property
 */
private class PropertiesDataFilter(
    private val resolutionFacade: ResolutionFacade,
    private val elements: List<KtElement>,
    private val searcher: JKInMemoryFilesSearcher,
    private val psiFactory: KtPsiFactory
) {
    fun filter(klass: KtClassOrObject, propertiesData: List<PropertyData>): List<PropertyWithAccessors> {
        val classDescriptor = klass.resolveToDescriptorIfAny(resolutionFacade) ?: return emptyList()
        val declarationDescriptors = classDescriptor.superClassAndSuperInterfaces().flatMap { superType ->
            superType.unsubstitutedMemberScope.getDescriptorsFiltered(DescriptorKindFilter.VARIABLES)
        }
        val variableNameToDescriptor = declarationDescriptors
            .asSequence()
            .filterIsInstance<VariableDescriptor>()
            .associateBy { it.name.asString() }

        return propertiesData.mapNotNull { getPropertyWithAccessors(it, klass, classDescriptor, variableNameToDescriptor) }
    }

    private fun getPropertyWithAccessors(
        propertyData: PropertyData,
        klass: KtClassOrObject,
        classDescriptor: ClassDescriptor,
        variableNameToDescriptor: Map<String, VariableDescriptor>
    ): PropertyWithAccessors? {
        val (realProperty, realGetter, realSetter, type) = propertyData

        fun renderType(): String? = type.takeUnless { it.isError }?.let { IdeDescriptorRenderers.SOURCE_CODE.renderType(it) }
            ?: realGetter?.function?.typeReference?.text
            ?: realSetter?.function?.valueParameters?.firstOrNull()?.typeReference?.text

        fun getterIsCompatibleWithSetter(): Boolean {
            if (realGetter == null || realSetter == null) return true
            if (klass.isInterfaceClass() && realGetter.function.hasOverrides() != realSetter.function.hasOverrides()) return false
            return true
        }

        fun getterUsesDifferentProperty(): Boolean =
            realProperty != null && realGetter?.target != null && realGetter.target != realProperty.property

        fun accessorsOverrideFunctions(): Boolean =
            realGetter?.function?.hasSuperFunction() == true || realSetter?.function?.hasSuperFunction() == true

        fun propertyIsAccessedBypassingNonPureAccessors(): Boolean {
            if (realProperty == null) return false
            if ((realGetter == null || realGetter.isPure) && (realSetter == null || realSetter.isPure)) return false

            if (!realProperty.property.isPrivate()) return true
            return realProperty.property.hasUsagesOutsideOf(
                inElement = klass.containingKtFile,
                outsideElements = listOfNotNull(realGetter?.function, realSetter?.function)
            )
        }

        fun propertyIsAccessedOnAnotherInstance(): Boolean {
            if (realProperty == null) return false

            if (realGetter != null) {
                val getFieldOfOtherInstanceInGetter = realProperty.property.usages(searcher).any { usage ->
                    val element = usage.safeAs<KtSimpleNameReference>()?.element ?: return@any false
                    val parent = element.parent
                    parent is KtQualifiedExpression
                            && !parent.receiverExpression.isReferenceToThis()
                            && realGetter.function.isAncestor(element)
                }
                if (getFieldOfOtherInstanceInGetter) return true
            }
            if (realSetter != null) {
                val assignFieldOfOtherInstance = realProperty.property.usages(searcher).any { usage ->
                    val element = usage.safeAs<KtSimpleNameReference>()?.element ?: return@any false
                    if (!element.readWriteAccess(useResolveForReadWrite = true).isWrite) return@any false
                    val parent = element.parent
                    parent is KtQualifiedExpression && !parent.receiverExpression.isReferenceToThis()
                }
                if (assignFieldOfOtherInstance) return true
            }

            return false
        }

        fun accessorsAreAnnotatedWithFunctionOnlyAnnotations(): Boolean {
            val descriptorToTargetPairs = listOf(
                realGetter?.function?.resolveToDescriptorIfAny(resolutionFacade) to "PROPERTY_GETTER",
                realSetter?.function?.resolveToDescriptorIfAny(resolutionFacade) to "PROPERTY_SETTER",
            )

            for ((functionDescriptor, requiredTarget) in descriptorToTargetPairs) {
                val hasInapplicableAnnotation = functionDescriptor?.annotations?.any { annotationDescriptor ->
                    val annotationClassDescriptor = annotationDescriptor.annotationClass ?: return@any false
                    val existingTargets = getExistingAnnotationTargets(annotationClassDescriptor)
                    existingTargets.contains("FUNCTION") && !existingTargets.contains(requiredTarget)
                } == true

                if (hasInapplicableAnnotation) return true
            }

            return false
        }

        fun createFakeGetter(name: String): FakeGetter? = when {
            // TODO write a test for this branch, may be related to KTIJ-8621, KTIJ-8673
            realProperty?.property?.resolveToDescriptorIfAny(resolutionFacade)?.overriddenDescriptors?.any {
                it.safeAs<VariableDescriptor>()?.isVar == true
            } == true -> FakeGetter(name, body = null, modifiersText = "")

            variableNameToDescriptor[name]?.let { variable ->
                variable.isVar && variable.containingDeclaration != classDescriptor
            } == true -> FakeGetter(name, body = psiFactory.createExpression("super.$name"), modifiersText = "")

            else -> null
        }

        fun createMergedProperty(name: String, renderedType: String): MergedProperty? =
            if (realGetter?.target != null
                && realGetter.target.name != realGetter.name
                && (realSetter == null || realSetter.target != null)
            ) MergedProperty(name, renderedType, isVar = realSetter != null, realGetter.target) else null

        fun createFakeSetter(name: String, mergedProperty: MergedProperty?): FakeSetter? = when {
            realProperty?.property?.isVar == true ->
                FakeSetter(name, body = null, modifiersText = "")

            // Var property cannot be overridden by val
            realProperty?.property?.resolveToDescriptorIfAny(resolutionFacade)?.overriddenDescriptors?.any {
                it.safeAs<VariableDescriptor>()?.isVar == true
            } == true
                    || variableNameToDescriptor[name]?.isVar == true ->
                FakeSetter(
                    name,
                    body = psiFactory.createBlock("super.$name = $name"),
                    modifiersText = ""
                )

            // Need setter with restricted visibility
            realGetter != null
                    && (realProperty != null
                    && realProperty.property.visibilityModifierTypeOrDefault() != realGetter.function.visibilityModifierTypeOrDefault()
                    && realProperty.property.isVar
                    || mergedProperty != null
                    && mergedProperty.mergeTo.visibilityModifierTypeOrDefault() != realGetter.function.visibilityModifierTypeOrDefault()
                    && mergedProperty.mergeTo.isVar
                    ) ->
                FakeSetter(name, body = null, modifiersText = null)

            else -> null
        }

        if (!getterIsCompatibleWithSetter() ||
            getterUsesDifferentProperty() ||
            accessorsOverrideFunctions() ||
            propertyIsAccessedBypassingNonPureAccessors() ||
            propertyIsAccessedOnAnotherInstance() ||
            accessorsAreAnnotatedWithFunctionOnlyAnnotations()
        ) return null

        val name = realGetter?.name ?: realSetter?.name ?: return null
        val renderedType = renderType() ?: return null

        // Avoid shadowing outer properties with same name
        if (realProperty == null && isNameShadowed(name, classDescriptor.containingDeclaration)) return null

        val getter = realGetter ?: createFakeGetter(name) ?: return null
        val mergedProperty = createMergedProperty(name, renderedType)
        val setter = realSetter ?: createFakeSetter(name, mergedProperty)

        val isVar = setter != null
        val property = mergedProperty?.copy(isVar = isVar)
            ?: realProperty?.copy(isVar = isVar)
            ?: FakeProperty(name, renderedType, isVar)

        return PropertyWithAccessors(property, getter, setter)
    }

    private fun KtNamedFunction.hasOverrides(): Boolean {
        val lightMethod = toLightMethods().singleOrNull() ?: return false
        if (OverridingMethodsSearch.search(lightMethod).findFirst() != null) return true
        if (elements.size == 1) return false
        return elements.any { element ->
            OverridingMethodsSearch.search(lightMethod, LocalSearchScope(element), true).findFirst() != null
        }
    }

    private fun KtNamedFunction.hasSuperFunction(): Boolean =
        resolveToDescriptorIfAny(resolutionFacade)?.original?.overriddenDescriptors?.isNotEmpty() == true

    private fun isNameShadowed(name: String, parent: DeclarationDescriptor?): Boolean {
        if (parent !is ClassDescriptor) return false
        val parentHasSameNameVariable =
            parent.unsubstitutedMemberScope.getDescriptorsFiltered(DescriptorKindFilter.VARIABLES) { it.asString() == name }.isNotEmpty()
        return parentHasSameNameVariable || isNameShadowed(name, parent.containingDeclaration)
    }

    private fun KtElement.hasUsagesOutsideOf(inElement: KtElement, outsideElements: List<KtElement>): Boolean =
        ReferencesSearch.search(this, LocalSearchScope(inElement)).any { reference ->
            outsideElements.none { it.isAncestor(reference.element) }
        }
}

private val redundantSetterModifiers: Set<KtModifierKeywordToken> = setOf(
    OVERRIDE_KEYWORD, FINAL_KEYWORD, OPEN_KEYWORD
)

private val redundantGetterModifiers: Set<KtModifierKeywordToken> = redundantSetterModifiers + setOf(
    PUBLIC_KEYWORD, INTERNAL_KEYWORD, PROTECTED_KEYWORD, PRIVATE_KEYWORD
)

private class ExternalProcessingUpdater(private val processing: NewExternalCodeProcessing) {
    context(KaSession)
    fun update(klass: KtClassOrObject, propertiesWithAccessors: List<PropertyWithAccessors>) {
        for (propertyWithAccessors in propertiesWithAccessors) {
            updateExternalProcessingInfo(klass, propertyWithAccessors)
        }
    }

    context(KaSession)
    private fun updateExternalProcessingInfo(klass: KtClassOrObject, propertyWithAccessors: PropertyWithAccessors) {
        val (property, getter, setter) = propertyWithAccessors

        // convenience variables
        val realGetter = getter as? RealGetter
        val realSetter = setter as? RealSetter

        fun KtNamedFunction.setPropertyForExternalProcessing(fieldData: JKFieldData) {
            val physicalMethodData = (processing.getMember(element = this) as? JKPhysicalMethodData) ?: return
            physicalMethodData.usedAsAccessorOfProperty = fieldData
        }

        val jkFieldData = when (property) {
            is RealProperty -> processing.getMember(property.property)
            is MergedProperty -> processing.getMember(property.mergeTo)
            is FakeProperty -> JKFakeFieldData(
                isStatic = klass is KtObjectDeclaration,
                kotlinElementPointer = null,
                fqName = klass.fqNameWithoutCompanions.child(Name.identifier(property.name)),
                name = property.name
            ).also { processing.addMember(it) }
        } as? JKFieldData

        if (jkFieldData != null) {
            jkFieldData.name = property.name
            realGetter?.function?.setPropertyForExternalProcessing(jkFieldData)
            realSetter?.function?.setPropertyForExternalProcessing(jkFieldData)
        }
    }
}

/**
 * Converts accessors to properties in a single class
 */
private class ClassConverter(
    private val searcher: JKInMemoryFilesSearcher,
    private val psiFactory: KtPsiFactory
) {
    fun convertClass(klass: KtClassOrObject, propertiesWithAccessors: List<PropertyWithAccessors>) {
        for (propertyWithAccessors in propertiesWithAccessors) {
            convert(klass, propertyWithAccessors)
        }
    }

    private fun convert(klass: KtClassOrObject, propertyWithAccessors: PropertyWithAccessors) {
        val (property, getter, setter) = propertyWithAccessors

        // convenience variables
        val realGetter = getter as? RealGetter
        val realSetter = setter as? RealSetter

        fun getKtProperty(): KtProperty = when (property) {
            is RealProperty -> {
                if (property.property.isVar != property.isVar) {
                    val newKeyword = if (property.isVar) psiFactory.createVarKeyword() else psiFactory.createValKeyword()
                    property.property.valOrVarKeyword.replace(newKeyword)
                }
                property.property
            }

            is FakeProperty -> {
                val newProperty = psiFactory.createProperty(property.name, property.type, property.isVar)
                val anchor = realGetter?.function ?: realSetter?.function
                klass.addDeclarationBefore(newProperty, anchor)
            }

            is MergedProperty -> property.mergeTo
        }

        val ktProperty = getKtProperty()
        val ktGetter = addGetter(getter, ktProperty, property.isFake)
        val ktSetter = setter?.let { addSetter(it, ktProperty, property.isFake) }
        val isOpen = realGetter?.function?.hasModifier(OPEN_KEYWORD) == true || realSetter?.function?.hasModifier(OPEN_KEYWORD) == true

        val getterVisibility = realGetter?.function?.visibilityModifierTypeOrDefault()
        if (getterVisibility != null) {
            ktProperty.setVisibility(getterVisibility)
        }

        fun removeRealAccessors() {
            if (realGetter != null) {
                if (realGetter.function.isAbstract() && !klass.isInterfaceClass()) {
                    ktProperty.addModifier(ABSTRACT_KEYWORD)
                    ktGetter.removeModifier(ABSTRACT_KEYWORD)
                }

                if (ktGetter.isRedundant()) {
                    realGetter.function.deleteExplicitLabelComments()
                    val commentSaver = CommentSaver(realGetter.function)
                    commentSaver.restore(ktProperty)
                }

                realGetter.function.delete()
            }

            if (realSetter != null) {
                if (ktSetter?.isRedundant() == true) {
                    realSetter.function.deleteExplicitLabelComments()
                    val commentSaver = CommentSaver(realSetter.function)
                    commentSaver.restore(ktProperty)
                }

                realSetter.function.delete()
            }
        }

        removeRealAccessors()

        // If getter & setter do not have backing fields we should remove initializer
        // As we already know that property is not directly used in the code
        if (getter.target == null && setter?.target == null) {
            ktProperty.initializer = null
        }

        if (property is MergedProperty) {
            ktProperty.renameTo(property.name)
        }
        if (isOpen) {
            ktProperty.addModifier(OPEN_KEYWORD)
        }

        moveAccessorAnnotationsToProperty(ktProperty)
        removeRedundantPropertyAccessors(ktProperty)
        convertGetterToSingleExpressionBody(ktProperty.getter)
    }

    private fun removeRedundantPropertyAccessors(property: KtProperty) {
        val getter = property.getter
        val setter = property.setter
        if (getter?.isRedundant() == true) removeRedundantGetter(getter)
        if (setter?.isRedundant() == true) removeRedundantSetter(setter)
    }

    private fun addGetter(getter: Getter, ktProperty: KtProperty, isFakeProperty: Boolean): KtPropertyAccessor {
        if (!isFakeProperty) {
            getter.body?.replacePropertyToFieldKeywordReferences(ktProperty)
        }

        val ktGetter = psiFactory.createGetter(getter.body, getter.modifiersText)
        for (modifier in redundantGetterModifiers) {
            ktGetter.removeModifier(modifier)
        }

        if (getter is RealGetter) {
            saveSurroundingComments(getter, ktGetter)

            // update original function references to property references
            getter.function.forAllUsages { usage ->
                val callExpression = usage.getStrictParentOfType<KtCallExpression>() ?: return@forAllUsages
                val qualifier = callExpression.getQualifiedExpressionForSelector()
                if (qualifier != null) {
                    qualifier.replace(psiFactory.createExpression("${qualifier.receiverExpression.text}.${getter.name}"))
                } else {
                    callExpression.replace(psiFactory.createExpression("this.${getter.name}"))
                }
            }
        }

        ktProperty.add(psiFactory.createNewLine(1))
        return ktProperty.add(ktGetter) as KtPropertyAccessor
    }

    private fun KtExpression.replacePropertyToFieldKeywordReferences(property: KtProperty) {
        val references = property.usages(searcher, scope = this).map { it.element }.ifEmpty { return }
        val fieldExpression = psiFactory.createExpression(FIELD_KEYWORD.value)

        for (reference in references) {
            val parent = reference.parent
            val referenceExpression = when {
                parent is KtQualifiedExpression && parent.receiverExpression.isReferenceToThis() -> parent
                else -> reference
            }

            referenceExpression.replace(fieldExpression)
        }
    }

    private fun KtPsiFactory.createGetter(body: KtExpression?, modifiers: String?): KtPropertyAccessor {
        val propertyText = "val x\n ${modifiers.orEmpty()} get" +
                when (body) {
                    is KtBlockExpression -> "() { return 1 }"
                    null -> ""
                    else -> "() = 1"
                } + "\n"
        val property = createProperty(propertyText)
        val getter = property.getter!!
        val bodyExpression = getter.bodyExpression

        bodyExpression?.replace(body!!)
        return getter
    }

    private fun saveSurroundingComments(accessor: RealAccessor, ktAccessor: KtPropertyAccessor) {
        if (accessor.function.firstChild is PsiComment) {
            ktAccessor.addBefore(psiFactory.createWhiteSpace(), ktAccessor.firstChild)
            ktAccessor.addBefore(accessor.function.firstChild, ktAccessor.firstChild)
        }
        if (accessor.function.lastChild is PsiComment) {
            ktAccessor.add(psiFactory.createWhiteSpace())
            ktAccessor.add(accessor.function.lastChild)
        }
    }

    private fun KtElement.forAllUsages(action: (KtElement) -> Unit) {
        usages(searcher).forEach { action(it.element as KtElement) }
    }

    private fun addSetter(setter: Setter, ktProperty: KtProperty, isFakeProperty: Boolean): KtPropertyAccessor {
        if (setter is RealSetter) {
            setter.function.valueParameters.single().rename(setter.parameterName)
        }

        if (!isFakeProperty) {
            setter.body?.replacePropertyToFieldKeywordReferences(ktProperty)
        }

        val modifiers = setter.modifiersText?.takeIf { it.isNotEmpty() }
            ?: setter.safeAs<RealSetter>()?.function?.visibilityModifierTypeOrDefault()?.value
            ?: ktProperty.visibilityModifierTypeOrDefault().value

        val ktSetter = psiFactory.createSetter(setter.body, setter.parameterName, modifiers)
        for (modifier in redundantSetterModifiers) {
            ktSetter.removeModifier(modifier)
        }

        val classVisibility = ktProperty.parentOfType<KtClassOrObject>()?.visibilityModifierTypeOrDefault()
        if (classVisibility == INTERNAL_KEYWORD || classVisibility == PUBLIC_KEYWORD) {
            ktSetter.removeModifier(PUBLIC_KEYWORD)
        }

        if (setter is RealSetter) {
            saveSurroundingComments(setter, ktSetter)
            val propertyName = ktProperty.name

            // update original function references to property references
            setter.function.forAllUsages { usage ->
                val callExpression = usage.getStrictParentOfType<KtCallExpression>() ?: return@forAllUsages
                val qualifier = callExpression.getQualifiedExpressionForSelector()
                val newValue = callExpression.valueArguments.single()
                if (qualifier != null) {
                    qualifier.replace(
                        psiFactory.createExpression("${qualifier.receiverExpression.text}.$propertyName = ${newValue.text}")
                    )
                } else {
                    callExpression.replace(psiFactory.createExpression("this.$propertyName = ${newValue.text}"))
                }
            }
        } else {
            val propertyVisibility = ktProperty.visibilityModifierTypeOrDefault()
            ktSetter.setVisibility(propertyVisibility)
        }

        return ktProperty.add(ktSetter) as KtPropertyAccessor
    }

    private fun KtPsiFactory.createSetter(body: KtExpression?, fieldName: String?, modifiers: String?): KtPropertyAccessor {
        val modifiersText = modifiers.orEmpty()
        val propertyText = when (body) {
            null -> "var x = 1\n  get() = 1\n $modifiersText set"
            is KtBlockExpression -> "var x get() = 1\n $modifiersText  set($fieldName) {\n field = $fieldName\n }"
            else -> "var x get() = 1\n $modifiersText set($fieldName) = TODO()"
        }
        val property = createProperty(propertyText)
        val setter = property.setter!!
        if (body != null) {
            setter.bodyExpression?.replace(body)
        }

        return setter
    }

    // A parameter with a special name (`field`) should be renamed
    private fun KtParameter.rename(newName: String) {
        if (name == newName) return

        val renamer = RenamePsiElementProcessor.forElement(this)
        val searchScope = project.projectScope() or useScope
        val findReferences = renamer.findReferences(this, searchScope, false)
        val usageInfos =
            findReferences.mapNotNull { reference ->
                val element = reference.element
                val isBackingField = element is KtNameReferenceExpression &&
                        element.text == FIELD_KEYWORD.value
                        && element.mainReference.resolve() == this
                        && isAncestor(element)
                if (isBackingField) return@mapNotNull null
                renamer.createUsageInfo(this, reference, reference.element)
            }.toTypedArray()
        renamer.renameElement(this, newName, usageInfos, null)
    }

    private fun KtProperty.renameTo(newName: String) {
        for (usage in usages(searcher)) {
            val element = usage.element
            val isBackingField = element is KtNameReferenceExpression
                    && element.text == FIELD_KEYWORD.value
                    && element.mainReference.resolve() == this
                    && isAncestor(element)
            if (isBackingField) continue
            val replacer =
                if (element.parent is KtQualifiedExpression) psiFactory.createExpression(newName)
                else psiFactory.createExpression("this.$newName")
            element.replace(replacer)
        }
        setName(newName)
    }

    private fun KtPropertyAccessor.isRedundant(): Boolean =
        // We need to ignore comments because there may be explicit label comments that we must preserve
        if (isGetter) isRedundantGetter(respectComments = false) else isRedundantSetter(respectComments = false)

    // Don't try to save the now useless explicit label comments,
    // they may hurt formatting later
    private fun KtNamedFunction.deleteExplicitLabelComments() {
        forEachDescendantOfType<PsiComment> { comment ->
            if (comment.text.asExplicitLabel() != null) comment.delete()
        }
    }

    private fun moveAccessorAnnotationsToProperty(property: KtProperty) {
        for (accessor in property.accessors.sortedBy { it.isGetter }) {
            for (accessorEntry in accessor.annotationEntries) {
                val propertyEntry = property.addAnnotationEntry(accessorEntry)
                val target = if (accessor.isGetter) PROPERTY_GETTER else PROPERTY_SETTER
                propertyEntry.addUseSiteTarget(target, property.project)
            }
            accessor.annotationEntries.forEach { it.delete() }
        }
    }

    private fun convertGetterToSingleExpressionBody(getter: KtPropertyAccessor?) {
        fun KtPropertyAccessor.singleBodyStatementExpression(): KtExpression? =
            bodyBlockExpression?.statements
                ?.singleOrNull()
                ?.safeAs<KtReturnExpression>()
                ?.takeIf { it.labeledExpression == null }
                ?.returnedExpression

        if (getter == null) return
        val body = getter.bodyExpression ?: return
        val returnedExpression = getter.singleBodyStatementExpression() ?: return

        val commentSaver = CommentSaver(body)
        getter.addBefore(KtPsiFactory(getter.project).createEQ(), body)
        val newBody = body.replaced(returnedExpression)
        commentSaver.restore(newBody)
    }
}

private fun KtElement.usages(searcher: JKInMemoryFilesSearcher, scope: PsiElement? = null): Iterable<PsiReference> =
    searcher.search(element = this, scope)

private fun ClassDescriptor.superClassAndSuperInterfaces(): List<ClassDescriptor> =
    getSuperInterfaces() + listOfNotNull(getSuperClassNotAny())

private fun KtExpression.isReferenceToThis(): Boolean {
    val reference = when (this) {
        is KtThisExpression -> instanceReference
        is KtReferenceExpression -> this
        is KtQualifiedExpression -> selectorExpression as? KtReferenceExpression
        else -> null
    }
    return reference?.resolve() == this.getStrictParentOfType<KtClassOrObject>()
}

private data class PropertyWithAccessors(
    val property: Property,
    val getter: Getter,
    val setter: Setter?
)

private data class PropertyData(
    val realProperty: RealProperty?,
    val realGetter: RealGetter?,
    val realSetter: RealSetter?,
    val type: KotlinType
)

private interface PropertyInfo {
    val name: String
}

private val PropertyInfo.isFake: Boolean
    get() = this is FakeAccessor

private interface Accessor : PropertyInfo {
    val target: KtProperty?
    val body: KtExpression?
    val modifiersText: String?
    val isPure: Boolean
}

private sealed class Property : PropertyInfo {
    abstract val isVar: Boolean
}

private data class RealProperty(
    val property: KtProperty,
    override val name: String,
    override val isVar: Boolean = property.isVar
) : Property()

private data class FakeProperty(override val name: String, val type: String, override val isVar: Boolean) : Property()

private data class MergedProperty(
    override val name: String,
    val type: String, // TODO unused!
    override val isVar: Boolean,
    val mergeTo: KtProperty
) : Property()

private sealed class Getter : Accessor

private sealed class Setter : Accessor {
    abstract val parameterName: String
}

private interface FakeAccessor : Accessor {
    override val target: KtProperty?
        get() = null
    override val isPure: Boolean
        get() = true
}

private interface RealAccessor : Accessor {
    val function: KtNamedFunction
    override val body: KtExpression?
        get() = function.bodyExpression
    override val modifiersText: String
        get() = function.modifierList?.text.orEmpty()

    fun updateTarget(newTarget: KtProperty): RealAccessor
}

private data class RealGetter(
    override val function: KtNamedFunction,
    override val target: KtProperty?,
    override val name: String,
    override val isPure: Boolean
) : Getter(), RealAccessor {
    override fun updateTarget(newTarget: KtProperty): RealGetter =
        copy(target = newTarget)
}

private data class FakeGetter(
    override val name: String,
    override val body: KtExpression?,
    override val modifiersText: String
) : Getter(), FakeAccessor

private data class RealSetter(
    override val function: KtNamedFunction,
    override val target: KtProperty?,
    override val name: String,
    override val isPure: Boolean
) : Setter(), RealAccessor {
    override val parameterName: String
        get() = (function.valueParameters.first().name ?: name).fixSetterParameterName()

    override fun updateTarget(newTarget: KtProperty): RealSetter =
        copy(target = newTarget)
}

private data class FakeSetter(
    override val name: String,
    override val body: KtExpression?,
    override val modifiersText: String?
) : Setter(), FakeAccessor {
    override val parameterName: String
        get() = name.fixSetterParameterName()
}

private fun String.fixSetterParameterName(): String =
    if (this == FIELD_KEYWORD.value) "value" else this
