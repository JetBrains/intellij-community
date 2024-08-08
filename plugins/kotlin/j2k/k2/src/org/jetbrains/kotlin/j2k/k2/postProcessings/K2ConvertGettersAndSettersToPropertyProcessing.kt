// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("DuplicatedCode")

package org.jetbrains.kotlin.j2k.k2.postProcessings

import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.OverridingMethodsSearch
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.parentOfType
import com.intellij.refactoring.rename.RenamePsiElementProcessor
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotation
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotationValue.ArrayValue
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotationValue.EnumEntryValue
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KaTypeRendererForSource
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.types.KaErrorType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.KaTypeNullability
import org.jetbrains.kotlin.asJava.toLightMethods
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget.PROPERTY_GETTER
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget.PROPERTY_SETTER
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.util.or
import org.jetbrains.kotlin.idea.base.util.projectScope
import org.jetbrains.kotlin.idea.codeinsight.utils.*
import org.jetbrains.kotlin.idea.refactoring.isAbstract
import org.jetbrains.kotlin.idea.refactoring.isInterfaceClass
import org.jetbrains.kotlin.idea.references.KtSimpleNameReference
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.references.readWriteAccess
import org.jetbrains.kotlin.idea.util.CommentSaver
import org.jetbrains.kotlin.j2k.*
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
import org.jetbrains.kotlin.types.Variance
import org.jetbrains.kotlin.util.capitalizeDecapitalize.decapitalizeAsciiOnly
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import org.jetbrains.kotlin.utils.mapToIndex

/**
 * This processing tries to convert functions that look like getters and setters
 * into a single property, taking into account various complicated rules
 * of what makes a legal Kotlin property (for example, regarding inheritance)
 */
internal class K2ConvertGettersAndSettersToPropertyProcessing : ElementsBasedPostProcessing() {
    private data class ApplicationInfo(
        val converter: ClassConverter,
        val klass: KtClassOrObject,
        val propertiesWithAccessors: List<PropertyWithAccessors>
    )

    // TODO:
    //  We can't apply for all classes at once, because it breaks inheritance cases.
    //  K1 version relies on sequential analysis and application to all classes.
    //  Test: org.jetbrains.kotlin.nj2k.NewJavaToKotlinConverterSingleFileTestGenerated.DetectProperties.testAccessorsImplementInterface
    private class Applier(private val infos: Set<ApplicationInfo>?) : PostProcessingApplier {
        override fun apply() {
            if (infos == null) return
            for ((converter, klass, propertiesWithAccessors) in infos) {
                // TODO change everything to PSI element pointers
                converter.convertClass(klass, propertiesWithAccessors)
            }
        }

        companion object {
            val EMPTY = Applier(infos = null)
        }
    }

    override fun runProcessing(elements: List<PsiElement>, converterContext: NewJ2kConverterContext) {
        error("Not supported in K2 J2K")
    }

    context(KaSession)
    override fun computeApplier(elements: List<PsiElement>, converterContext: NewJ2kConverterContext): PostProcessingApplier {
        val ktElements = elements.filterIsInstance<KtElement>().ifEmpty { return Applier.EMPTY }
        val psiFactory = KtPsiFactory(converterContext.project)
        val searcher = JKInMemoryFilesSearcher.create(ktElements)

        val collector = PropertiesDataCollector(searcher)
        val filter = PropertiesDataFilter(ktElements, searcher, psiFactory)
        val externalProcessingUpdater = ExternalProcessingUpdater(converterContext.externalCodeProcessor)
        val converter = ClassConverter(searcher, psiFactory)

        val classes = ktElements
            .descendantsOfType<KtClassOrObject>()
            // KtEnumEntrySymbol is not a KtClassOrObjectSymbol, so we skip enum entries here
            // TODO handle enum overrides (KTIJ-29782)
            .filterNot { it is KtEnumEntry }
            .sortedByInheritance()
        val classesWithPropertiesData = collector.collectPropertiesData(classes).ifEmpty { return Applier.EMPTY }
        val infos = mutableSetOf<ApplicationInfo>()

        for ((klass, propertiesData) in classesWithPropertiesData) {
            val propertiesWithAccessors = filter.filter(klass, propertiesData)
            infos.add(ApplicationInfo(converter, klass, propertiesWithAccessors))
            externalProcessingUpdater.update(klass, propertiesWithAccessors)
        }

        return Applier(infos)
    }

    context(KaSession)
    private fun List<KtClassOrObject>.sortedByInheritance(): List<KtClassOrObject> {
        val sorted = mutableListOf<KtClassOrObject>()
        val visited = Array(size) { false }

        val symbols = mapNotNull { it.symbol as? KaClassSymbol }
        val symbolToIndex = symbols.mapToIndex()
        val outers = symbols.map { symbol ->
            symbol.superClassAndSuperInterfaces().mapNotNull { symbolToIndex[it] }
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

        for (index in symbols.indices) {
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
private class PropertiesDataCollector(private val searcher: JKInMemoryFilesSearcher) {
    private val propertyNameToSuperType: MutableMap<Pair<KtClassOrObject, String>, KaType> = mutableMapOf()

    context(KaSession)
    fun collectPropertiesData(classes: List<KtClassOrObject>): List<Pair<KtClassOrObject, List<PropertyData>>> {
        val result = classes.map { klass -> klass to collectPropertiesData(klass) }
        propertyNameToSuperType.clear() // flush KaTypes
        return result
    }

    context(KaSession)
    private fun collectPropertiesData(klass: KtClassOrObject): List<PropertyData> {
        val propertyInfos: List<PropertyInfo> = klass.declarations.mapNotNull { it.asPropertyInfo() }
        val propertyInfoGroups: Collection<List<PropertyInfo>> =
            propertyInfos.groupBy { it.name.removePrefix("is").decapitalizeAsciiOnly() }.values

        val propertyDataList = propertyInfoGroups.mapNotNull { group -> collectPropertyData(klass, group) }
        return propertyDataList
    }

    context(KaSession)
    @OptIn(KaExperimentalApi::class)
    private fun collectPropertyData(klass: KtClassOrObject, propertyInfoGroup: List<PropertyInfo>): PropertyData? {
        val property = propertyInfoGroup.firstIsInstanceOrNull<RealProperty>()
        val getter = propertyInfoGroup.firstIsInstanceOrNull<RealGetter>()
        val setter = propertyInfoGroup.firstIsInstanceOrNull<RealSetter>()?.takeIf { setterCandidate ->
            if (getter == null) return@takeIf true
            val getterType = getter.function.symbol.returnType
            val setterType = setterCandidate.function.symbol.valueParameters.first().returnType

            // The inferred nullability of accessors may be different due to semi-random reasons,
            // so we check types compatibility ignoring nullability. Anyway, the final property type will be nullable if necessary.
            getterType.isSubtypeOf(setterType.withNullability(KaTypeNullability.NULLABLE))
        }

        val accessor = getter ?: setter ?: return null
        val name = accessor.name
        val functionSymbol = accessor.function.symbol as? KaNamedFunctionSymbol ?: return null

        // Fun interfaces cannot have abstract properties
        if (klass.isSamSymbol(functionSymbol)) return null

        val superDeclarationOwner = functionSymbol.getSuperDeclarationOwner()
        val type = propertyNameToSuperType[superDeclarationOwner to name]
            ?: calculatePropertyType(getter?.function, setter?.function)
            ?: return null
        propertyNameToSuperType[klass to name] = type

        val renderedType =
            type.takeIf { it !is KaErrorType }?.render(KaTypeRendererForSource.WITH_SHORT_NAMES, position = Variance.INVARIANT)
                ?: getter?.function?.typeReference?.text
                ?: setter?.function?.valueParameters?.firstOrNull()?.typeReference?.text

        return PropertyData(
            property,
            getter?.withTargetSet(property),
            setter?.withTargetSet(property),
            renderedType
        )
    }

    context(KaSession)
    private fun KtDeclaration.asPropertyInfo(): PropertyInfo? {
        return when (this) {
            is KtProperty -> RealProperty(this, name ?: return null)
            is KtNamedFunction -> asGetter() ?: asSetter()
            else -> null
        }
    }

    context(KaSession)
    private fun KtNamedFunction.asGetter(): Getter? {
        val name = name?.asGetterName() ?: return null
        if (valueParameters.isNotEmpty()) return null
        if (typeParameters.isNotEmpty()) return null

        val returnExpression = bodyExpression?.statements()?.singleOrNull() as? KtReturnExpression
        val property = returnExpression?.returnedExpression?.unpackedReferenceToProperty()
        val getterType = type()
        val singleTimeUsedTarget = property?.takeIf {
            it.containingClass() == containingClass() && getterType != null && it.type()?.semanticallyEquals(getterType) == true
        }

        return RealGetter(this, singleTimeUsedTarget, name, singleTimeUsedTarget != null)
    }

    context(KaSession)
    private fun KtNamedFunction.asSetter(): Setter? {
        val name = name?.asSetterName() ?: return null
        val parameter = valueParameters.singleOrNull() ?: return null
        if (typeParameters.isNotEmpty()) return null

        if (!symbol.returnType.isUnitType) return null

        val binaryExpression = bodyExpression?.statements()?.singleOrNull() as? KtBinaryExpression
        val property = binaryExpression?.let { expression ->
            if (expression.operationToken != EQ) return@let null
            val right = expression.right as? KtNameReferenceExpression ?: return@let null
            if (right.resolve() != parameter) return@let null
            expression.left?.unpackedReferenceToProperty()
        }
        val singleTimeUsedTarget = property?.takeIf {
            val parameterType = parameter.type() ?: return@takeIf false
            it.containingClass() == containingClass() && it.type()?.semanticallyEquals(parameterType) == true
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

    context(KaSession)
    private fun KtClassOrObject.isSamSymbol(functionSymbol: KaNamedFunctionSymbol): Boolean {
        if (functionSymbol.modality != KaSymbolModality.ABSTRACT) return false
        val classSymbol = classSymbol as? KaNamedClassSymbol ?: return false
        if (!classSymbol.isFun) return false

        val callableSymbolsWithSameName = classSymbol.declaredMemberScope.callables(functionSymbol.name)
        return callableSymbolsWithSameName.filter { it is KaNamedFunctionSymbol && it.modality == KaSymbolModality.ABSTRACT }.count() == 1
    }

    context(KaSession)
    private fun KaFunctionSymbol.getSuperDeclarationOwner(): KtClassOrObject? {
        val overriddenDeclaration = directlyOverriddenSymbols.firstOrNull()?.psi as? KtDeclaration
        return overriddenDeclaration?.containingClassOrObject
    }

    context(KaSession)
    private fun calculatePropertyType(getter: KtNamedFunction?, setter: KtNamedFunction?): KaType? {
        val getterType = getter?.symbol?.returnType
        val setterType = setter?.symbol?.valueParameters?.singleOrNull()?.returnType
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
    private val elements: List<KtElement>,
    private val searcher: JKInMemoryFilesSearcher,
    private val psiFactory: KtPsiFactory
) {
    context(KaSession)
    fun filter(klass: KtClassOrObject, propertiesData: List<PropertyData>): List<PropertyWithAccessors> {
        val classSymbol = klass.classSymbol ?: return emptyList()
        val superSymbols = classSymbol.superClassAndSuperInterfaces()

        // TODO
        //  There is a problem when we first convert a getter to property of a super interface
        //  and then we need to extract the new property from the super interface scope to convert the subclass.
        //  It looks like we need some reanalysis that is not happening with Analysis API
        //  (i.e., we are getting the previous getter instead of a property, and so cannot properly convert the subclass).
        //  Tests:
        //   - org.jetbrains.kotlin.nj2k.NewJavaToKotlinConverterMultiFileTestGenerated.testDetectPropertiesMultipleFiles
        //   - org.jetbrains.kotlin.nj2k.NewJavaToKotlinConverterMultiFileTestGenerated.testInterfaceWithGetterInOtherFile
        val superVariableSymbols = superSymbols.flatMap { superSymbol ->
            superSymbol.declaredMemberScope.callables().filterIsInstance<KaVariableSymbol>()
        }
        val superVariableNameToSymbol = superVariableSymbols.associateBy { it.name.toString() }

        return propertiesData.mapNotNull { getPropertyWithAccessors(it, klass, classSymbol, superVariableNameToSymbol) }
    }

    context(KaSession)
    private fun getPropertyWithAccessors(
        propertyData: PropertyData,
        klass: KtClassOrObject,
        classSymbol: KaClassSymbol,
        superVariableNameToSymbol: Map<String, KaVariableSymbol>
    ): PropertyWithAccessors? {
        val (realProperty, realGetter, realSetter, renderedType) = propertyData

        val name = realGetter?.name ?: realSetter?.name ?: return null
        if (renderedType == null) return null

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

        // If a real accessor is annotated with an annotation with the "FUNCTION" target only,
        // we can't change it into a property accessor
        fun accessorsAreAnnotatedWithFunctionOnlyAnnotations(): Boolean {
            fun KaDeclarationSymbol.getExistingAnnotationTargets(): Set<String> {
                val targetAnnotation = annotations.firstOrNull { it.classId == StandardNames.FqNames.targetClassId } ?: return emptySet()
                val targets = (targetAnnotation.arguments.firstOrNull()?.expression as? ArrayValue)?.values ?: return emptySet()
                return targets.mapNotNull { (it as? EnumEntryValue)?.callableId?.callableName?.asString() }.toSet()
            }

            fun KaAnnotation.isInapplicable(requiredTarget: String): Boolean {
                val annotationSymbol = constructorSymbol?.containingDeclaration ?: return false
                val existingTargets = annotationSymbol.getExistingAnnotationTargets()
                return existingTargets.contains("FUNCTION") && !existingTargets.contains(requiredTarget)
            }

            val symbolToTargetPairs = listOf(
                realGetter?.function?.symbol to "PROPERTY_GETTER",
                realSetter?.function?.symbol to "PROPERTY_SETTER",
            )

            for ((functionSymbol, requiredTarget) in symbolToTargetPairs) {
                val accessorAnnotations = functionSymbol?.annotations ?: continue
                val hasInapplicableAnnotation = accessorAnnotations.any { it.isInapplicable(requiredTarget) }
                if (hasInapplicableAnnotation) return true
            }

            return false
        }

        fun createFakeGetter(name: String): FakeGetter? {
            return when {
                // TODO write a test for this branch, may be related to KTIJ-8621, KTIJ-8673
                realProperty?.property?.overridesVarProperty() == true ->
                    FakeGetter(name, body = null, modifiersText = "")

                superVariableNameToSymbol[name]?.let { variable ->
                    !variable.isVal && variable.containingSymbol != classSymbol
                } == true -> FakeGetter(name, body = psiFactory.createExpression("super.$name"), modifiersText = "")

                else -> null
            }
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
            realProperty?.property?.overridesVarProperty() == true || superVariableNameToSymbol[name]?.isVal == false ->
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

        // Avoid shadowing outer properties with the same name
        if (realProperty == null && isNameShadowed(name, classSymbol.containingSymbol)) return null

        val getter = realGetter ?: createFakeGetter(name) ?: return null
        val mergedProperty = createMergedProperty(name, renderedType)
        val setter = realSetter ?: createFakeSetter(name, mergedProperty)

        val isVar = setter != null
        val property = mergedProperty?.copy(isVar = isVar)
            ?: realProperty?.copy(isVar = isVar)
            ?: FakeProperty(name, renderedType, isVar)

        return PropertyWithAccessors(property, getter, setter)
    }

    context(KaSession)
    private fun KtProperty.overridesVarProperty(): Boolean =
        symbol.directlyOverriddenSymbols.any { it.safeAs<KaVariableSymbol>()?.isVal == false }

    private fun KtNamedFunction.hasOverrides(): Boolean {
        val lightMethod = toLightMethods().singleOrNull() ?: return false
        if (OverridingMethodsSearch.search(lightMethod).findFirst() != null) return true
        if (elements.size == 1) return false
        return elements.any { element ->
            OverridingMethodsSearch.search(lightMethod, LocalSearchScope(element), true).findFirst() != null
        }
    }

    context(KaSession)
    private fun KtNamedFunction.hasSuperFunction(): Boolean =
        symbol.directlyOverriddenSymbols.toList().isNotEmpty()

    context(KaSession)
    private fun isNameShadowed(name: String, parent: KaSymbol?): Boolean {
        if (parent !is KaClassSymbol) return false
        val parentHasSameNamedVariable = parent.declaredMemberScope
            .callables { it.asString() == name }
            .filterIsInstance<KaVariableSymbol>()
            .toList()
            .isNotEmpty()
        return parentHasSameNamedVariable || isNameShadowed(name, parent.containingSymbol)
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
        val ktPsiFactory = KtPsiFactory(property.project)

        fun KtAnnotationEntry.addUseSiteTarget(useSiteTarget: AnnotationUseSiteTarget) {
            replace(ktPsiFactory.createAnnotationEntry("@${useSiteTarget.renderName}:${text.drop(1)}"))
        }

        for (accessor in property.accessors.sortedBy { it.isGetter }) {
            for (accessorEntry in accessor.annotationEntries) {
                val propertyEntry = property.addAnnotationEntry(accessorEntry)
                val target = if (accessor.isGetter) PROPERTY_GETTER else PROPERTY_SETTER
                propertyEntry.addUseSiteTarget(target)
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

context(KaSession)
private fun KaClassSymbol.superClassAndSuperInterfaces(): List<KaClassSymbol> {
    return superTypes.filter { !it.isAnyType }.mapNotNull { it.expandedSymbol }
}

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
    val renderedType: String?
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

context(KaSession)
private fun KtDeclaration.type(): KaType? =
    (symbol as? KaCallableSymbol)?.returnType