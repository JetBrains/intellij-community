// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k

import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.JavaRecursiveElementWalkingVisitor
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiField
import com.intellij.psi.PsiImportList
import com.intellij.psi.PsiImportStatementBase
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiPackageStatement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiRecordComponent
import com.intellij.psi.PsiStatement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.JavaPsiRecordUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.projectStructure.KaSourceModule
import org.jetbrains.kotlin.config.LanguageVersionSettingsImpl
import org.jetbrains.kotlin.idea.base.projectStructure.getKaModuleOfTypeSafe
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.projectStructure.toKaSourceModuleWithElementSourceModuleKindOrProduction
import org.jetbrains.kotlin.j2k.ConversionResult
import org.jetbrains.kotlin.j2k.ConverterContext
import org.jetbrains.kotlin.j2k.ConverterSettings
import org.jetbrains.kotlin.j2k.ElementResult
import org.jetbrains.kotlin.j2k.IdeaReferenceSearcher
import org.jetbrains.kotlin.j2k.J2kPostprocessorExtension
import org.jetbrains.kotlin.j2k.J2kPreprocessorExtension
import org.jetbrains.kotlin.j2k.ParseContext.CODE_BLOCK
import org.jetbrains.kotlin.j2k.ParseContext.TOP_LEVEL
import org.jetbrains.kotlin.j2k.PostProcessingTarget.MultipleFilesPostProcessingTarget
import org.jetbrains.kotlin.j2k.PostProcessor
import org.jetbrains.kotlin.j2k.ReferenceSearcher
import org.jetbrains.kotlin.j2k.Result
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.nj2k.externalCodeProcessing.J2kMemberKey
import org.jetbrains.kotlin.nj2k.externalCodeProcessing.OriginalJavaPsiContext
import org.jetbrains.kotlin.nj2k.externalCodeProcessing.buildLightMethodKey
import org.jetbrains.kotlin.nj2k.externalCodeProcessing.buildMemberKey
import org.jetbrains.kotlin.nj2k.externalCodeProcessing.NewExternalCodeProcessing
import org.jetbrains.kotlin.nj2k.printing.JKCodeBuilder
import org.jetbrains.kotlin.nj2k.types.JKTypeFactory
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtImportList
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.isAncestor
import org.jetbrains.kotlin.resolve.ImportPath

@ApiStatus.Internal
class JavaToKotlinConverter(
    val project: Project,
    val targetModule: Module?,
    val settings: ConverterSettings,
    val targetFile: KtFile? = null,
    val referenceSearcher: ReferenceSearcher = IdeaReferenceSearcher,
) {
    val phasesCount: Int = J2KConversionPhase.entries.size

    suspend fun filesToKotlin(
        files: List<PsiJavaFile>,
        postProcessor: PostProcessor,
        preprocessorExtensions: List<J2kPreprocessorExtension>,
        postprocessorExtensions: List<J2kPostprocessorExtension>,
    ): ConversionResult {
        if (files.isEmpty()) return ConversionResult(emptyMap(), null)

        val copiedFiles = readAction {
            files.map {
                val copy = it.copy() as PsiJavaFile
                anchorCopiedElementToOriginal(it, copy)
                copy
            }
        }

        PreprocessorExtensionsRunner.runProcessors(project, copiedFiles, preprocessorExtensions)

        return filesToKotlin(
            files = files,
            copiedFiles = copiedFiles,
            postProcessor = postProcessor,
            postprocessorExtensions = postprocessorExtensions,
        )
    }

    private suspend fun filesToKotlin(
        files: List<PsiJavaFile>,
        copiedFiles: List<PsiJavaFile>,
        postProcessor: PostProcessor,
        postprocessorExtensions: List<J2kPostprocessorExtension>,
    ): ConversionResult {

        val (results, externalCodeProcessing, context) = readAction {
            val originalJavaPsiContext = OriginalJavaPsiContext(
                originalMembersByKey = files.buildMembersByKey(),
                originalClassesByQualifiedName = files.buildClassesByQualifiedName(),
            )
            val semanticResolver = OriginalJavaSemanticResolver(originalJavaPsiContext)
            val methodReferenceSearcher = MethodReferenceSearcher(
                copiedFiles = copiedFiles,
                semanticResolver = semanticResolver,
                delegate = referenceSearcher,
            )
            val inMemoryConverter = JavaToKotlinConverter(project, targetModule, settings, targetFile, methodReferenceSearcher)

            inMemoryConverter.elementsToKotlin(
                contextElement = files.first(),
                inputElements = copiedFiles,
                conversionContextElements = files,
                originalJavaPsiContext = originalJavaPsiContext,
            )
        }

        val kotlinFiles = results.filterNotNull().mapIndexed { i, result ->
            val javaFile = files[i]
            edtWriteAction {
                KtPsiFactory.contextual(javaFile.parent ?: javaFile)
                    .createPhysicalFile(javaFile.name.replace(".java", ".kt"), result.text)
                    .also { it.addImports(result.importsToAdd) }
            }
        }

        postProcessor.doAdditionalProcessing(MultipleFilesPostProcessingTarget(kotlinFiles), context)
        PostprocessorExtensionsRunner.runProcessors(project, kotlinFiles, postprocessorExtensions)

        val (javaLines, kotlinLines) = readAction {
            files.sumOf { StringUtil.getLineBreakCount(it.text) } to kotlinFiles.sumOf { StringUtil.getLineBreakCount(it.text) }
        }

        return ConversionResult(files.zip(kotlinFiles.map { it.text }).toMap(), externalCodeProcessing, javaLines, kotlinLines)
    }

    fun elementsToKotlin(
        inputElements: List<PsiElement>,
        forInlining: Boolean = false
    ): Result {
        val contextElement = inputElements.firstOrNull() ?: return Result.EMPTY
        return elementsToKotlin(contextElement, inputElements, inputElements, forInlining)
    }

    private fun elementsToKotlin(
        contextElement: PsiElement,
        inputElements: List<PsiElement>,
        conversionContextElements: List<PsiElement>,
        forInlining: Boolean = false,
        originalJavaPsiContext: OriginalJavaPsiContext = OriginalJavaPsiContext.Empty,
    ): Result {
        val targetPlatform = (targetFile?.getKaModuleOfTypeSafe<KaSourceModule>(project, useSiteModule = null)
        // This `KaSourceModule` is not 100% waterproof, but without the target file, we don't actually know the kind of the target
        // source module. The most reasonable assumption is that we copy the input element to a source module of the same kind.
            ?: targetModule?.toKaSourceModuleWithElementSourceModuleKindOrProduction(contextElement)
            ?: return Result.EMPTY).targetPlatform

        val resolver = JKResolver(project, targetModule, contextElement)
        val semanticResolver = OriginalJavaSemanticResolver(originalJavaPsiContext)
        val symbolProvider = JKSymbolProvider(resolver, semanticResolver)
        val typeFactory = JKTypeFactory(symbolProvider, semanticResolver)
        symbolProvider.typeFactory = typeFactory
        symbolProvider.preBuildTree(inputElements)

        val languageVersionSettings = when {
            contextElement.isPhysical -> contextElement.languageVersionSettings
            else -> LanguageVersionSettingsImpl.DEFAULT
        }

        val importStorage = JKImportStorage(targetPlatform, project)
        val treeBuilder = JavaToJKTreeBuilder(symbolProvider, typeFactory, semanticResolver, referenceSearcher, importStorage, forInlining)

        // we want to leave all imports as is in the case when user is converting only imports
        val saveImports = inputElements.all { element ->
            element is PsiComment || element is PsiWhiteSpace
                    || element is PsiImportStatementBase || element is PsiImportList
                    || element is PsiPackageStatement
        }

        val elementsWithAsts = inputElements.associateWith { treeBuilder.buildTree(it, saveImports) }

        fun isInConversionContext(element: PsiElement): Boolean =
            conversionContextElements.any { it == element || it.isAncestor(element, strict = true) }

        val externalCodeProcessing = NewExternalCodeProcessing(referenceSearcher, ::isInConversionContext, originalJavaPsiContext)
        val context = ConverterContext(
            symbolProvider,
            typeFactory,
            converter = this,
            importStorage,
            JKElementInfoStorage(),
            externalCodeProcessing,
            languageVersionSettings,
            settings
        )

        val treeRoots = elementsWithAsts.mapNotNull { it.value }
        ConversionsRunner.doApply(treeRoots, context)

        val results = elementsWithAsts.map { (element, ast) ->
            if (ast == null) return@map null

            val code = JKCodeBuilder(context).printCodeOut(ast)
            val importsToAdd = importStorage.getImports()
            val parseContext = when (element) {
                is PsiStatement, is PsiExpression -> CODE_BLOCK
                else -> TOP_LEVEL
            }

            ElementResult(code, importsToAdd, parseContext)
        }

        return Result(
            results,
            externalCodeProcessing.takeIf { it.isExternalProcessingNeeded() },
            context
        )
    }

    private fun List<PsiJavaFile>.buildMembersByKey(): Map<J2kMemberKey, PsiMember> {
        val membersByKey = mutableMapOf<J2kMemberKey, PsiMember>()
        for (file in this) {
            file.accept(object : JavaRecursiveElementWalkingVisitor() {
                override fun visitField(field: PsiField) {
                    super.visitField(field)
                    field.buildMemberKey()?.let { membersByKey[it] = field }
                }

                override fun visitMethod(method: PsiMethod) {
                    super.visitMethod(method)
                    method.buildMemberKey()?.let { membersByKey[it] = method }
                }

                override fun visitRecordComponent(recordComponent: PsiRecordComponent) {
                    super.visitRecordComponent(recordComponent)
                    val accessor = JavaPsiRecordUtil.getAccessorForRecordComponent(recordComponent) ?: return
                    accessor.buildLightMethodKey()?.let { membersByKey[it] = accessor }
                }
            })
        }
        return membersByKey
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

    private class MethodReferenceSearcher(
        copiedFiles: List<PsiJavaFile>,
        private val semanticResolver: OriginalJavaSemanticResolver,
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
            delegate.findLocalUsages(semanticResolver.originalElementOrSelf(element), semanticResolver.originalElementOrSelf(scope))

        override fun hasInheritors(`class`: PsiClass): Boolean {
            if (!`class`.isInCopiedFiles()) return delegate.hasInheritors(`class`)

            val originalClass = semanticResolver.resolveClass(`class`) ?: return false
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

            val originalMethod = semanticResolver.resolveMember(method) as? PsiMethod ?: return false
            return delegate.hasOverrides(originalMethod)
        }

        override fun findUsagesForExternalCodeProcessing(
            element: PsiElement,
            searchJava: Boolean,
            searchKotlin: Boolean
        ): Collection<PsiReference> {
            if (!element.isInCopiedFiles()) {
                return delegate.findUsagesForExternalCodeProcessing(element, searchJava, searchKotlin)
            }

            val originalElement = when (element) {
                is PsiClass -> semanticResolver.resolveClass(element)
                is PsiMember -> semanticResolver.resolveMember(element)
                else -> null
            } ?: return emptyList()

            return delegate.findUsagesForExternalCodeProcessing(originalElement, searchJava, searchKotlin)
        }

        private fun PsiElement.isInCopiedFiles(): Boolean = containingFile in copiedFiles
    }

    companion object {
        fun KtFile.addImports(imports: Collection<FqName>) {
            if (imports.isEmpty()) return

            val psiFactory = KtPsiFactory(project)

            @Suppress("DEPRECATION") // unclear how to replace this
            val importPsi = psiFactory.createImportDirectives(
                imports.map { ImportPath(it, isAllUnder = false) }
            )
            val createdImportList = importPsi.first().parent as KtImportList
            val importList = importList
            if (importList == null) {
                val newImportList = addImportList(createdImportList)
                newImportList.ensureLineBreaksAfter(psiFactory)
            } else {
                val updatedList = if (importList.firstChild != null) {
                    createdImportList.addRangeBefore(importList.firstChild, importList.lastChild, createdImportList.firstChild)
                } else createdImportList
                val result = importList.replace(updatedList)
                result.ensureLineBreaksAfter(psiFactory)
            }

            packageDirective?.ensureLineBreaksAfter(psiFactory)
        }

        private fun KtFile.addImportList(importList: KtImportList): KtImportList {
            if (packageDirective != null) {
                return addAfter(importList, packageDirective) as KtImportList
            }

            val firstDeclaration = findChildByClass(KtDeclaration::class.java)
            return if (firstDeclaration != null) {
                addBefore(importList, firstDeclaration) as KtImportList
            } else {
                add(importList) as KtImportList
            }
        }

        private fun PsiElement.ensureLineBreaksAfter(psiFactory: KtPsiFactory) {
            if (text.isBlank()) return
            val nextWhiteSpace = (nextSibling as? PsiWhiteSpace)?.text ?: ""
            val numberOfNewLinesToAdd = when {
                nextWhiteSpace.startsWith("\n\n") -> return
                nextWhiteSpace.startsWith("\n") -> 1
                else -> 2
            }
            parent?.addAfter(psiFactory.createNewLine(numberOfNewLinesToAdd), /* anchor = */ this)
        }
    }

}

private enum class J2KConversionPhase {
    PREPROCESSING,
    BUILD_AST,
    RUN_CONVERSIONS,
    PRINT_CODE,
    CREATE_FILES
}
