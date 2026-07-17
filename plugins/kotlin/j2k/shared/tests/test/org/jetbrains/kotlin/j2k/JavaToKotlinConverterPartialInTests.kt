// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.j2k

import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.JavaRecursiveElementWalkingVisitor
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiNewExpression
import com.intellij.psi.PsiPrimitiveType
import com.intellij.psi.PsiRecordComponent
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiType
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.JavaPsiRecordUtil
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KaTypeRendererForSource
import org.jetbrains.kotlin.j2k.PostProcessingTarget.MultipleFilesPostProcessingTarget
import org.jetbrains.kotlin.nj2k.JavaToKotlinConverter
import org.jetbrains.kotlin.nj2k.PostprocessorExtensionsRunner
import org.jetbrains.kotlin.nj2k.PreprocessorExtensionsRunner
import org.jetbrains.kotlin.psi.KtAnonymousInitializer
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtSecondaryConstructor
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.types.Variance

private val originalPsiPointerKeyForTests = Key.create<SmartPsiElementPointer<PsiElement>>("j2k.test.original.psi.pointer")

suspend fun JavaToKotlinConverter.filesToKotlinPartiallyInTests(
    files: List<PsiJavaFile>,
    postProcessor: PostProcessor,
    selectedDeclaration: PsiElement,
    preprocessorExtensions: List<J2kPreprocessorExtension>,
    postprocessorExtensions: List<J2kPostprocessorExtension>,
): ConversionResult {
    if (files.isEmpty()) return ConversionResult(emptyMap(), null)

    val partialDeclarationAnchors = readAction { files.collectPartialDeclarationAnchors(setOf(selectedDeclaration)) }
    check(partialDeclarationAnchors.size == 1) {
        "Partial in-memory conversion could not anchor the selected declaration"
    }

    val copiedFiles = readAction {
        files.map { it.copy() as PsiJavaFile }.also { copiedFiles ->
            anchorCopiedFilesToOriginalsForTests(files, copiedFiles)
        }
    }
    PreprocessorExtensionsRunner.runProcessors(project, copiedFiles, preprocessorExtensions)

    val selectedCopiedDeclaration = readAction {
        partialDeclarationAnchors.resolveDeclarations(
            classesByQualifiedName = copiedFiles.buildClassesByQualifiedName(),
            membersByKey = copiedFiles.buildMembersByKey(),
            declarationsByStructuralKey = copiedFiles.buildDeclarationsByStructuralKey(),
        )
    }?.singleOrNull() ?: error("Partial in-memory conversion could not resolve the selected declaration on copied PSI")

    val partialSemantics = readAction { copiedFiles.buildPartialSemantics(selectedCopiedDeclaration) }
    val selectedFieldInitializerTexts = readAction {
        if (selectedDeclaration !is PsiField) return@readAction emptyMap()
        val fieldKey = selectedDeclaration.buildKotlinMemberKeyForTests() ?: return@readAction emptyMap()
        val initializerText = selectedDeclaration.convertInitializerForPartialTests(this@filesToKotlinPartiallyInTests) ?: return@readAction emptyMap()
        mapOf(fieldKey to initializerText)
    }

    val originalMembersByKey = readAction { files.buildMembersByKey() }
    val originalClassesByQualifiedName = readAction { files.buildClassesByQualifiedName() }
    val partialConverter = JavaToKotlinConverter(
        project = project,
        targetModule = targetModule,
        settings = settings,
        targetFile = targetFile,
        referenceSearcher = InMemoryReferenceSearcherForTests(
            copiedFiles = copiedFiles,
            originalMembersByKey = originalMembersByKey,
            originalClassesByQualifiedName = originalClassesByQualifiedName,
            delegate = referenceSearcher,
        ),
    )

    val result = readAction {
        partialConverter.elementsToKotlinWithOriginalContextForTests(
            files = files,
            copiedFiles = copiedFiles,
            originalMembersByKey = files.buildMembersByJ2kKeyForTests(),
        )
    }
    val context = requireNotNull(result.converterContext)

    val kotlinFiles = mutableListOf<KtFile>()
    for ((index, elementResult) in result.results.filterNotNull().withIndex()) {
        val javaFile = files[index]
        val ktFile = edtWriteAction {
            KtPsiFactory.contextual(javaFile.parent ?: javaFile)
                .createPhysicalFile(javaFile.name.replace(".java", ".kt"), elementResult.text)
                .also { createdFile ->
                    with(JavaToKotlinConverter) {
                        createdFile.addImports(elementResult.importsToAdd)
                    }
                }
        }
        rewriteToPartialSemantics(ktFile, partialSemantics[index], selectedFieldInitializerTexts)
        kotlinFiles += ktFile
    }

    postProcessor.doAdditionalProcessing(MultipleFilesPostProcessingTarget(kotlinFiles), context)
    PostprocessorExtensionsRunner.runProcessors(project, kotlinFiles, postprocessorExtensions)
    for (kotlinFile in kotlinFiles) {
        rewriteVarargShadowPropertiesForPartialTests(kotlinFile)
    }

    val (javaLines, kotlinLines) = readAction {
        files.sumOf { StringUtil.getLineBreakCount(it.text) } to kotlinFiles.sumOf { StringUtil.getLineBreakCount(it.text) }
    }

    return ConversionResult(files.zip(kotlinFiles.map(KtFile::getText)).toMap(), result.externalCodeProcessing, javaLines, kotlinLines)
}

@OptIn(KaExperimentalApi::class)
private suspend fun rewriteToPartialSemantics(
    ktFile: KtFile,
    partialSemantics: PartialSemanticsForTests,
    selectedFieldInitializerTexts: Map<KotlinFieldKeyForTests, String>,
): Unit {
    val (psiFactory, propertiesToStub, propertyTypes, propertiesNeedingImplicitInitializer, implicitInitializers, selectedFieldInitializersToRestore, selectedParameterShadowProperties, functionsToStub, constructorsToStub, initializersToStub, classesNeedingInit) = readAction {
        val psiFactory = KtPsiFactory(ktFile.project)
        val propertiesToStub = ktFile.collectDescendantsOfType<KtProperty>().filter {
            !it.hasDelegate() && it.getter == null && it.setter == null && !partialSemantics.isSelectedField(it)
        }
        val propertiesNeedingImplicitInitializer = ktFile.collectDescendantsOfType<KtProperty>().filter {
            !it.hasDelegate() && it.getter == null && it.setter == null && partialSemantics.isSelectedField(it) && it.initializer == null
        }
        val propertyTypes: Map<KtProperty, String> = analyze(ktFile) {
            (propertiesToStub + propertiesNeedingImplicitInitializer).associateWith { property ->
                property.renderTypeForPartialTests()
            }
        }
        val implicitInitializers = propertiesNeedingImplicitInitializer.associateWith { property ->
            partialSemantics.defaultInitializer(property) ?: defaultInitializerForPartialTests(propertyTypes.getValue(property))
        }
        val selectedFieldInitializersToRestore = ktFile.collectDescendantsOfType<KtProperty>().mapNotNull { property ->
            val key = property.buildMemberKeyForTests() ?: return@mapNotNull null
            val initializerText = selectedFieldInitializerTexts[key] ?: return@mapNotNull null
            if (property.initializer?.text != "TODO()") return@mapNotNull null
            property to initializerText
        }
        val selectedParameterShadowProperties = ktFile.collectParameterShadowPropertiesForPartialTests()
        val functionsToStub = ktFile.collectDescendantsOfType<KtNamedFunction>().filter {
            it.bodyExpression != null && !partialSemantics.isSelectedMethod(it)
        }
        val constructorsToStub = ktFile.collectDescendantsOfType<KtSecondaryConstructor>()
        val initializersToStub = ktFile.collectDescendantsOfType<KtAnonymousInitializer>()
        val classesNeedingInit = ktFile.collectDescendantsOfType<KtClass>().filter { partialSemantics.needsSyntheticInit(it) }
        RewriteData(
            psiFactory,
            propertiesToStub,
            propertyTypes,
            propertiesNeedingImplicitInitializer,
            implicitInitializers,
            selectedFieldInitializersToRestore,
            selectedParameterShadowProperties,
            functionsToStub,
            constructorsToStub,
            initializersToStub,
            classesNeedingInit,
        )
    }

    edtWriteAction {
        for (property in propertiesToStub) {
            if (property.typeReference == null) {
                property.typeReference = psiFactory.createType(propertyTypes.getValue(property))
            }
            property.initializer = psiFactory.createExpression("TODO()")
        }

        for (property in propertiesNeedingImplicitInitializer) {
            val (typeText, initializerText) = implicitInitializers.getValue(property)
            if (property.typeReference?.text != typeText) {
                property.typeReference = psiFactory.createType(typeText)
            }
            property.initializer = psiFactory.createExpression(initializerText)
        }

        for ((property, initializerText) in selectedFieldInitializersToRestore) {
            property.initializer = psiFactory.createExpression(initializerText)
        }

        for ((property, parameterName) in selectedParameterShadowProperties) {
            property.typeReference = null
            property.initializer = psiFactory.createExpression(parameterName)
        }

        for (function in functionsToStub) {
            function.bodyExpression?.replace(psiFactory.createBlock("TODO()"))
        }

        for (constructor in constructorsToStub) {
            constructor.replace(psiFactory.createSecondaryConstructor(constructor.stubTextForPartialTests()))
        }

        for (initializer in initializersToStub) {
            initializer.body?.replace(psiFactory.createBlock("TODO()"))
        }

        for (klass in classesNeedingInit) {
            if (klass.getAnonymousInitializers().isEmpty()) {
                if (klass.body == null) {
                    klass.replace(psiFactory.createClassWithSyntheticInitForPartialTests(klass.text))
                } else {
                    val body = klass.body ?: continue
                    val companionObject = klass.declarations.filterIsInstance<KtObjectDeclaration>().firstOrNull { it.isCompanion() }
                    val initializer = if (companionObject != null) {
                        body.addBefore(psiFactory.createAnonymousInitializer(), companionObject)
                    } else {
                        klass.addDeclaration(psiFactory.createAnonymousInitializer())
                    }
                    (initializer as? KtAnonymousInitializer)?.body?.replace(psiFactory.createBlock("TODO()"))
                }
            }
        }

        CodeStyleManager.getInstance(ktFile.project).reformat(ktFile)
    }
}

private suspend fun rewriteVarargShadowPropertiesForPartialTests(ktFile: KtFile) {
    val psiFactory = KtPsiFactory(ktFile.project)
    val selectedVarargShadowProperties = readAction { ktFile.collectParameterShadowPropertiesForPartialTests() }
    edtWriteAction {
        for ((property, parameterName) in selectedVarargShadowProperties) {
            property.typeReference = null
            property.initializer = psiFactory.createExpression(parameterName)
        }
        CodeStyleManager.getInstance(ktFile.project).reformat(ktFile)
    }
}

private fun KtFile.collectParameterShadowPropertiesForPartialTests(): List<Pair<KtProperty, String>> =
    collectDescendantsOfType<KtNamedFunction>().flatMap { function ->
        val body = function.bodyExpression ?: return@flatMap emptyList()
        function.valueParameters.mapNotNull { parameter ->
            body.collectDescendantsOfType<KtProperty>().firstOrNull { property ->
                property.name == parameter.name && property.initializer?.text == "TODO()"
            }?.let { property -> property to parameter.name.orEmpty() }
        }
    }

private fun KtSecondaryConstructor.stubTextForPartialTests(): String {
    val modifierText = modifierList?.text?.takeIf { it.isNotBlank() }?.plus(" ") ?: ""
    return "$modifierText constructor${valueParameterList?.text.orEmpty()} { TODO() }"
}

private fun KtPsiFactory.createClassWithSyntheticInitForPartialTests(classText: String): KtClass =
    (createFile("dummy.kt", "$classText {\n    init {\n        TODO()\n    }\n}").declarations.single() as KtClass)

private fun PsiField.convertInitializerForPartialTests(converter: JavaToKotlinConverter): String? {
    val initializer = initializer ?: return null
    val converted = converter.elementsToKotlin(listOf(initializer)).results.singleOrNull()?.text
    if (converted != null && converted != "TODO()") return converted

    return when (initializer) {
        is PsiNewExpression -> initializer.classReference?.referenceName?.let { className ->
            val arguments = initializer.argumentList?.expressions?.joinToString(", ") { argument ->
                converter.elementsToKotlin(listOf(argument)).results.singleOrNull()?.text ?: argument.text
            }.orEmpty()
            "$className($arguments)"
        }

        else -> null
    }
}

@OptIn(KaExperimentalApi::class)
private data class RewriteData(
    val psiFactory: KtPsiFactory,
    val propertiesToStub: List<KtProperty>,
    val propertyTypes: Map<KtProperty, String>,
    val propertiesNeedingImplicitInitializer: List<KtProperty>,
    val implicitInitializers: Map<KtProperty, Pair<String, String>>,
    val selectedFieldInitializersToRestore: List<Pair<KtProperty, String>>,
    val selectedParameterShadowProperties: List<Pair<KtProperty, String>>,
    val functionsToStub: List<KtNamedFunction>,
    val constructorsToStub: List<KtSecondaryConstructor>,
    val initializersToStub: List<KtAnonymousInitializer>,
    val classesNeedingInit: List<KtClass>,
)

private data class PartialSemanticsForTests(
    val selectedClasses: Set<String>,
    val selectedFields: Set<KotlinFieldKeyForTests>,
    val selectedFieldInitializerKinds: Map<KotlinFieldKeyForTests, SelectedFieldInitializerKindForTests>,
    val selectedMethods: Set<KotlinMethodKeyForTests>,
    val classesNeedingSyntheticInit: Set<String>,
) {
    fun isSelectedField(property: KtProperty): Boolean = property.buildMemberKeyForTests() in selectedFields

    fun isSelectedMethod(function: KtNamedFunction): Boolean = function.buildMethodKeyForTests() in selectedMethods

    fun defaultInitializer(property: KtProperty): Pair<String, String>? {
        val fieldKind = property.buildMemberKeyForTests()?.let(selectedFieldInitializerKinds::get) ?: return null
        return when (fieldKind) {
            SelectedFieldInitializerKindForTests.BOOLEAN -> "Boolean" to "false"
            SelectedFieldInitializerKindForTests.CHAR -> "Char" to "0.toChar()"
            SelectedFieldInitializerKindForTests.PRIMITIVE -> "Int" to "0"
            SelectedFieldInitializerKindForTests.REFERENCE -> "${property.renderTypeForPartialTests().removeSuffix("?")}?" to "null"
        }
    }

    fun needsSyntheticInit(klass: KtClass): Boolean =
        klass.fqName?.asString() in classesNeedingSyntheticInit &&
                klass.getAnonymousInitializers().isEmpty() &&
                klass.secondaryConstructors.isEmpty()
}

private fun JavaToKotlinConverter.elementsToKotlinWithOriginalContextForTests(
    files: List<PsiJavaFile>,
    copiedFiles: List<PsiJavaFile>,
    originalMembersByKey: Map<Any, PsiMember>,
): Result {
    val method = JavaToKotlinConverter::class.java.declaredMethods.single { candidate ->
        candidate.name == "elementsToKotlin" && candidate.parameterCount == 5
    }
    method.isAccessible = true
    val contextClass = Class.forName("org.jetbrains.kotlin.nj2k.externalCodeProcessing.OriginalJavaPsiContext")
    val contextConstructor = contextClass.declaredConstructors.single { it.parameterCount == 2 }
    contextConstructor.isAccessible = true
    return method.invoke(
        this,
        files.first(),
        copiedFiles,
        files,
        false,
        contextConstructor.newInstance(originalMembersByKey, files.buildClassesByQualifiedName()),
    ) as Result
}

@OptIn(KaExperimentalApi::class)
private fun KtProperty.renderTypeForPartialTests(): String =
    typeReference?.text ?: analyze(this) {
        symbol.returnType.render(KaTypeRendererForSource.WITH_SHORT_NAMES, position = Variance.INVARIANT)
    }

private fun defaultInitializerForPartialTests(typeText: String): Pair<String, String> {
    val normalizedType = typeText.removeSuffix("?")
    if (typeText.endsWith("?")) return typeText to "null"

    return when (normalizedType) {
        "Boolean" -> typeText to "false"
        "Byte", "Short", "Int", "Long", "Float", "Double" -> typeText to "0"
        "Char" -> typeText to "0.toChar()"
        else -> "$typeText?" to "null"
    }
}

private fun PsiField.defaultInitializerKindForTests(): SelectedFieldInitializerKindForTests = when {
    type == PsiType.BOOLEAN -> SelectedFieldInitializerKindForTests.BOOLEAN
    type == PsiType.CHAR -> SelectedFieldInitializerKindForTests.CHAR
    type is PsiPrimitiveType -> SelectedFieldInitializerKindForTests.PRIMITIVE
    else -> SelectedFieldInitializerKindForTests.REFERENCE
}

private fun List<PsiJavaFile>.buildPartialSemantics(selectedCopiedDeclaration: PsiElement): List<PartialSemanticsForTests> {
    return map { file ->
        val selectedClasses = mutableSetOf<String>()
        val selectedFields = mutableSetOf<KotlinFieldKeyForTests>()
        val selectedFieldInitializerKinds = mutableMapOf<KotlinFieldKeyForTests, SelectedFieldInitializerKindForTests>()
        val selectedMethods = mutableSetOf<KotlinMethodKeyForTests>()
        val classesNeedingSyntheticInit = mutableSetOf<String>()

        file.accept(object : JavaRecursiveElementWalkingVisitor() {
            override fun visitClass(aClass: PsiClass) {
                if (aClass == selectedCopiedDeclaration) {
                    aClass.qualifiedName?.let(selectedClasses::add)
                } else if (aClass.constructors.isNotEmpty() || aClass.initializers.isNotEmpty()) {
                    aClass.qualifiedName?.let(classesNeedingSyntheticInit::add)
                }
                super.visitClass(aClass)
            }

            override fun visitField(field: PsiField) {
                if (field == selectedCopiedDeclaration) {
                    field.buildKotlinMemberKeyForTests()?.let { key ->
                        selectedFields += key
                        selectedFieldInitializerKinds[key] = field.defaultInitializerKindForTests()
                    }
                }
                super.visitField(field)
            }

            override fun visitMethod(method: PsiMethod) {
                if (method == selectedCopiedDeclaration) {
                    method.buildKotlinMemberKeyForTests()?.let(selectedMethods::add)
                }
                super.visitMethod(method)
            }
        })

        PartialSemanticsForTests(selectedClasses, selectedFields, selectedFieldInitializerKinds, selectedMethods, classesNeedingSyntheticInit)
    }
}

private data class KotlinFieldKeyForTests(
    val ownerQualifiedName: String,
    val name: String,
)

private sealed interface TestMemberKey {
    data class Method(
        val ownerQualifiedName: String,
        val name: String,
        val parameterTypes: List<String>,
    ) : TestMemberKey

    data class Field(
        val ownerQualifiedName: String,
        val name: String,
    ) : TestMemberKey
}

private data class KotlinMethodKeyForTests(
    val ownerQualifiedName: String,
    val name: String,
    val parameterCount: Int,
)

private fun PsiField.buildKotlinMemberKeyForTests(): KotlinFieldKeyForTests? =
    containingClass?.qualifiedName?.let { KotlinFieldKeyForTests(it, name) }

private fun PsiMethod.buildKotlinMemberKeyForTests(): KotlinMethodKeyForTests? =
    containingClass?.qualifiedName?.let { KotlinMethodKeyForTests(it, name, parameterList.parametersCount) }

private fun KtProperty.containingClassQualifiedNameForTests(): String? = containingClassOrObjectForTests()?.ownerQualifiedNameForTests()

private fun KtNamedFunction.containingClassQualifiedNameForTests(): String? = containingClassOrObjectForTests()?.ownerQualifiedNameForTests()

private fun KtProperty.buildMemberKeyForTests(): KotlinFieldKeyForTests? =
    containingClassQualifiedNameForTests()?.let { ownerQualifiedName ->
        name?.let { KotlinFieldKeyForTests(ownerQualifiedName, it) }
    }

private fun KtNamedFunction.buildMethodKeyForTests(): KotlinMethodKeyForTests? =
    containingClassQualifiedNameForTests()?.let { ownerQualifiedName ->
        name?.let { KotlinMethodKeyForTests(ownerQualifiedName, it, valueParameters.size) }
    }

private fun KtProperty.containingClassOrObjectForTests(): KtClassOrObject? =
    PsiTreeUtil.getParentOfType(this, KtClassOrObject::class.java)

private fun KtNamedFunction.containingClassOrObjectForTests(): KtClassOrObject? =
    PsiTreeUtil.getParentOfType(this, KtClassOrObject::class.java)

private fun KtClassOrObject.ownerQualifiedNameForTests(): String? = when {
    this is KtObjectDeclaration && isCompanion() -> PsiTreeUtil.getParentOfType(this, KtClassOrObject::class.java)?.fqName?.asString()
    else -> fqName?.asString()
}

private fun PsiMember.buildMemberKeyForTests(): TestMemberKey? {
    val ownerQualifiedName = containingClass?.qualifiedName ?: return null
    return when (this) {
        is PsiMethod -> TestMemberKey.Method(
            ownerQualifiedName = ownerQualifiedName,
            name = name,
            parameterTypes = parameterList.parameters.map { it.type.canonicalText },
        )

        is PsiField -> TestMemberKey.Field(
            ownerQualifiedName = ownerQualifiedName,
            name = name,
        )

        else -> null
    }
}

private fun List<PsiJavaFile>.buildMembersByKey(): Map<TestMemberKey, PsiMember> {
    val membersByKey = mutableMapOf<TestMemberKey, PsiMember>()
    for (file in this) {
        file.accept(object : JavaRecursiveElementWalkingVisitor() {
            override fun visitField(field: PsiField) {
                super.visitField(field)
                field.buildMemberKeyForTests()?.let { membersByKey[it] = field }
            }

            override fun visitMethod(method: PsiMethod) {
                super.visitMethod(method)
                method.buildMemberKeyForTests()?.let { membersByKey[it] = method }
            }

            override fun visitRecordComponent(recordComponent: PsiRecordComponent) {
                super.visitRecordComponent(recordComponent)
                val accessor = JavaPsiRecordUtil.getAccessorForRecordComponent(recordComponent) ?: return
                accessor.buildMemberKeyForTests()?.let { membersByKey[it] = accessor }
            }
        })
    }
    return membersByKey
}

private fun List<PsiJavaFile>.buildMembersByJ2kKeyForTests(): Map<Any, PsiMember> {
    val membersByKey = mutableMapOf<Any, PsiMember>()
    for (file in this) {
        file.accept(object : JavaRecursiveElementWalkingVisitor() {
            override fun visitField(field: PsiField) {
                super.visitField(field)
                field.buildJ2kMemberKeyForTests()?.let { membersByKey[it] = field }
            }

            override fun visitMethod(method: PsiMethod) {
                super.visitMethod(method)
                method.buildJ2kMemberKeyForTests()?.let { membersByKey[it] = method }
            }

            override fun visitRecordComponent(recordComponent: PsiRecordComponent) {
                super.visitRecordComponent(recordComponent)
                val accessor = JavaPsiRecordUtil.getAccessorForRecordComponent(recordComponent) ?: return
                accessor.buildJ2kLightMethodKeyForTests()?.let { membersByKey[it] = accessor }
            }
        })
    }
    return membersByKey
}

private fun PsiMember.buildJ2kMemberKeyForTests(): Any? {
    val method = externalCodeProcessingKtClass.declaredMethods.single { candidate ->
        candidate.name == "buildMemberKey" && candidate.parameterCount == 1 && PsiMember::class.java.isAssignableFrom(candidate.parameterTypes.single())
    }
    method.isAccessible = true
    return method.invoke(null, this)
}

private fun PsiMethod.buildJ2kLightMethodKeyForTests(): Any? {
    val method = externalCodeProcessingKtClass.declaredMethods.single { candidate ->
        candidate.name == "buildLightMethodKey" && candidate.parameterCount == 1 && PsiMethod::class.java.isAssignableFrom(candidate.parameterTypes.single())
    }
    method.isAccessible = true
    return method.invoke(null, this)
}

private val externalCodeProcessingKtClass: Class<*> =
    Class.forName("org.jetbrains.kotlin.nj2k.externalCodeProcessing.NewExternalCodeProcessingKt")

private enum class SelectedFieldInitializerKindForTests {
    BOOLEAN,
    CHAR,
    PRIMITIVE,
    REFERENCE,
}

private fun List<PsiJavaFile>.buildClassesByQualifiedName(): Map<String, PsiClass> {
    val classesByQualifiedName = mutableMapOf<String, PsiClass>()
    for (file in this) {
        file.accept(object : JavaRecursiveElementWalkingVisitor() {
            override fun visitClass(aClass: PsiClass) {
                super.visitClass(aClass)
                aClass.qualifiedName?.let { classesByQualifiedName[it] = aClass }
            }
        })
    }
    return classesByQualifiedName
}

private fun anchorCopiedFilesToOriginalsForTests(originalFiles: List<PsiJavaFile>, copiedFiles: List<PsiJavaFile>) {
    check(originalFiles.size == copiedFiles.size)
    originalFiles.zip(copiedFiles).forEach { (originalFile, copiedFile) ->
        anchorCopiedElementToOriginalForTests(originalFile, copiedFile)
    }
}

private fun anchorCopiedElementToOriginalForTests(original: PsiElement, copied: PsiElement) {
    copied.putUserData(originalPsiPointerKeyForTests, SmartPointerManager.createPointer(original))

    var originalChild = original.firstChild
    var copiedChild = copied.firstChild
    while (originalChild != null && copiedChild != null) {
        anchorCopiedElementToOriginalForTests(originalChild, copiedChild)
        originalChild = originalChild.nextSibling
        copiedChild = copiedChild.nextSibling
    }
}

private fun PsiElement.originalElementForTests(): PsiElement? =
    getUserData(originalPsiPointerKeyForTests)?.element

private fun PsiElement.originalElementOrSelfForTests(): PsiElement =
    originalElementForTests() ?: this

private class InMemoryReferenceSearcherForTests(
    copiedFiles: List<PsiJavaFile>,
    private val originalMembersByKey: Map<TestMemberKey, PsiMember>,
    private val originalClassesByQualifiedName: Map<String, PsiClass>,
    private val delegate: ReferenceSearcher,
) : ReferenceSearcher {
    private val copiedFiles = copiedFiles.toSet()
    private val copiedMethods = buildList {
        copiedFiles.forEach { file ->
            file.accept(object : JavaRecursiveElementWalkingVisitor() {
                override fun visitMethod(method: PsiMethod) {
                    super.visitMethod(method)
                    add(method)
                }
            })
        }
    }

    override fun findLocalUsages(element: PsiElement, scope: PsiElement): Collection<PsiReference> =
        delegate.findLocalUsages(element.originalElementOrSelfForTests(), scope.originalElementOrSelfForTests())

    override fun hasInheritors(`class`: PsiClass): Boolean {
        if (!`class`.isInCopiedFiles()) return delegate.hasInheritors(`class`)

        val originalClass = `class`.qualifiedName?.let(originalClassesByQualifiedName::get) ?: return false
        return delegate.hasInheritors(originalClass)
    }

    override fun hasOverrides(method: PsiMethod): Boolean {
        if (!method.isInCopiedFiles()) return delegate.hasOverrides(method)

        if (copiedMethods.any { candidate ->
                candidate != method && candidate.findSuperMethods(false).any { superMethod -> superMethod == method }
            }
        ) {
            return true
        }

        val originalMethod = method.buildMemberKeyForTests()?.let(originalMembersByKey::get) as? PsiMethod ?: return false
        return delegate.hasOverrides(originalMethod)
    }

    override fun findUsagesForExternalCodeProcessing(
        element: PsiElement,
        searchJava: Boolean,
        searchKotlin: Boolean,
    ): Collection<PsiReference> {
        if (!element.isInCopiedFiles()) {
            return delegate.findUsagesForExternalCodeProcessing(element, searchJava, searchKotlin)
        }

        val originalElement = when (element) {
            is PsiClass -> element.qualifiedName?.let(originalClassesByQualifiedName::get)
            is PsiMember -> element.buildMemberKeyForTests()?.let(originalMembersByKey::get)
            else -> null
        } ?: return emptyList()

        return delegate.findUsagesForExternalCodeProcessing(originalElement, searchJava, searchKotlin)
    }

    private fun PsiElement.isInCopiedFiles(): Boolean = containingFile in copiedFiles
}

private fun List<PsiJavaFile>.collectPartialDeclarationAnchors(selectedDeclarations: Set<PsiElement>): List<PartialDeclarationAnchorForTests> {
    if (selectedDeclarations.isEmpty()) return emptyList()

    val anchors = mutableListOf<PartialDeclarationAnchorForTests>()
    for ((fileIndex, file) in withIndex()) {
        var classOrdinal = 0
        var fieldOrdinal = 0
        var methodOrdinal = 0
        file.accept(object : JavaRecursiveElementWalkingVisitor() {
            override fun visitClass(aClass: PsiClass) {
                val structuralKey = PartialDeclarationStructuralKeyForTests(fileIndex, PartialDeclarationKindForTests.CLASS, classOrdinal++)
                super.visitClass(aClass)
                if (aClass !in selectedDeclarations) return
                anchors += PartialDeclarationAnchorForTests(classQualifiedName = aClass.qualifiedName, structuralKey = structuralKey)
            }

            override fun visitField(field: PsiField) {
                val structuralKey = PartialDeclarationStructuralKeyForTests(fileIndex, PartialDeclarationKindForTests.FIELD, fieldOrdinal++)
                super.visitField(field)
                if (field !in selectedDeclarations) return
                anchors += PartialDeclarationAnchorForTests(memberKey = field.buildMemberKeyForTests(), structuralKey = structuralKey)
            }

            override fun visitMethod(method: PsiMethod) {
                val structuralKey = PartialDeclarationStructuralKeyForTests(fileIndex, PartialDeclarationKindForTests.METHOD, methodOrdinal++)
                super.visitMethod(method)
                if (method !in selectedDeclarations) return
                anchors += PartialDeclarationAnchorForTests(memberKey = method.buildMemberKeyForTests(), structuralKey = structuralKey)
            }
        })
    }
    return anchors
}

private fun List<PsiJavaFile>.buildDeclarationsByStructuralKey(): Map<PartialDeclarationStructuralKeyForTests, PsiElement> {
    val declarationsByStructuralKey = mutableMapOf<PartialDeclarationStructuralKeyForTests, PsiElement>()
    for ((fileIndex, file) in withIndex()) {
        var classOrdinal = 0
        var fieldOrdinal = 0
        var methodOrdinal = 0
        file.accept(object : JavaRecursiveElementWalkingVisitor() {
            override fun visitClass(aClass: PsiClass) {
                declarationsByStructuralKey[PartialDeclarationStructuralKeyForTests(fileIndex, PartialDeclarationKindForTests.CLASS, classOrdinal++)] = aClass
                super.visitClass(aClass)
            }

            override fun visitField(field: PsiField) {
                declarationsByStructuralKey[PartialDeclarationStructuralKeyForTests(fileIndex, PartialDeclarationKindForTests.FIELD, fieldOrdinal++)] = field
                super.visitField(field)
            }

            override fun visitMethod(method: PsiMethod) {
                declarationsByStructuralKey[PartialDeclarationStructuralKeyForTests(fileIndex, PartialDeclarationKindForTests.METHOD, methodOrdinal++)] = method
                super.visitMethod(method)
            }
        })
    }
    return declarationsByStructuralKey
}

private fun List<PartialDeclarationAnchorForTests>.resolveDeclarations(
    classesByQualifiedName: Map<String, PsiClass>,
    membersByKey: Map<TestMemberKey, PsiMember>,
    declarationsByStructuralKey: Map<PartialDeclarationStructuralKeyForTests, PsiElement>,
): Set<PsiElement>? {
    val resolvedDeclarations = linkedSetOf<PsiElement>()
    for ((classQualifiedName, memberKey, structuralKey) in this) {
        val declaration =
            classQualifiedName?.let(classesByQualifiedName::get)
                ?: memberKey?.let(membersByKey::get)
                ?: declarationsByStructuralKey[structuralKey]
                ?: return null
        resolvedDeclarations += declaration
    }
    return resolvedDeclarations
}

private data class PartialDeclarationAnchorForTests(
    val classQualifiedName: String? = null,
    val memberKey: TestMemberKey? = null,
    val structuralKey: PartialDeclarationStructuralKeyForTests,
)

private data class PartialDeclarationStructuralKeyForTests(
    val fileIndex: Int,
    val declarationKind: PartialDeclarationKindForTests,
    val ordinalInFile: Int,
)

private enum class PartialDeclarationKindForTests {
    CLASS,
    FIELD,
    METHOD,
}
