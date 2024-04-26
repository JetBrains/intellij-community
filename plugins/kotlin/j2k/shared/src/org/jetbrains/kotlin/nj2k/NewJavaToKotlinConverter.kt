// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisOnEdt
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.config.LanguageVersionSettingsImpl
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.projectStructure.productionOrTestSourceModuleInfo
import org.jetbrains.kotlin.idea.base.projectStructure.toKtModule
import org.jetbrains.kotlin.j2k.*
import org.jetbrains.kotlin.j2k.PostProcessingTarget.MultipleFilesPostProcessingTarget
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.nj2k.J2KConversionPhase.*
import org.jetbrains.kotlin.nj2k.externalCodeProcessing.NewExternalCodeProcessing
import org.jetbrains.kotlin.nj2k.printing.JKCodeBuilder
import org.jetbrains.kotlin.nj2k.types.JKTypeFactory
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtImportList
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.isAncestor
import org.jetbrains.kotlin.resolve.ImportPath

class NewJavaToKotlinConverter(
    val project: Project,
    val targetModule: Module?,
    val settings: ConverterSettings,
    val targetFile: KtFile? = null
) : JavaToKotlinConverter() {
    val phasesCount = J2KConversionPhase.entries.size
    val referenceSearcher: ReferenceSearcher = IdeaReferenceSearcher
    private val phaseDescription: String = KotlinNJ2KBundle.message("phase.converting.j2k")

    override fun filesToKotlin(
        files: List<PsiJavaFile>,
        postProcessor: PostProcessor,
        progressIndicator: ProgressIndicator
    ): FilesResult = filesToKotlin(files, postProcessor, progressIndicator, bodyFilter = null)

    fun filesToKotlin(
        files: List<PsiJavaFile>,
        postProcessor: PostProcessor,
        progressIndicator: ProgressIndicator,
        bodyFilter: ((PsiElement) -> Boolean)?,
    ): FilesResult {
        val withProgressProcessor = NewJ2kWithProgressProcessor(progressIndicator, files, postProcessor.phasesCount + phasesCount)
        return withProgressProcessor.process {
            val (results, externalCodeProcessing, context) = runReadAction {
                elementsToKotlin(files, withProgressProcessor, bodyFilter)
            }
            val kotlinFiles = results.mapIndexed { i, result ->
                runUndoTransparentActionInEdt(inWriteAction = true) {
                    val javaFile = files[i]
                    withProgressProcessor.updateState(fileIndex = i, phase = CREATE_FILES, phaseDescription)
                    KtPsiFactory.contextual(files[i]).createPhysicalFile(javaFile.name.replace(".java", ".kt"), result!!.text)
                        .also { it.addImports(result.importsToAdd) }
                }
            }

            postProcessor.doAdditionalProcessing(MultipleFilesPostProcessingTarget(kotlinFiles), context) { phase, description ->
                withProgressProcessor.updateState(fileIndex = null, phase = phase + phasesCount, description = description)
            }
            FilesResult(kotlinFiles.map { it.text }, externalCodeProcessing)
        }
    }

    @OptIn(KtAllowAnalysisOnEdt::class)
    fun elementsToKotlin(
        inputElements: List<PsiElement>,
        processor: WithProgressProcessor,
        bodyFilter: ((PsiElement) -> Boolean)?,
        forInlining: Boolean = false
    ): Result = allowAnalysisOnEdt {
        val contextElement = inputElements.firstOrNull() ?: return Result.EMPTY
        val targetKtModule = targetModule?.productionOrTestSourceModuleInfo?.toKtModule()

        // TODO
        // val originKtModule = ProjectStructureProvider.getInstance(project).getModule(contextElement, contextualModule = null)
        // doesn't work for copy-pasted code, in this case the module is NotUnderContentRootModuleByModuleInfo, which can't be analyzed

        when {
            targetKtModule != null -> {
                analyze(targetKtModule) {
                    doConvertElementsToKotlin(contextElement, inputElements, processor, bodyFilter, forInlining)
                }
            }

            targetFile != null -> {
                analyze(targetFile) {
                    doConvertElementsToKotlin(contextElement, inputElements, processor, bodyFilter, forInlining)
                }
            }

            else -> Result.EMPTY
        }
    }

    context(KtAnalysisSession)
    private fun doConvertElementsToKotlin(
        contextElement: PsiElement,
        inputElements: List<PsiElement>,
        processor: WithProgressProcessor,
        bodyFilter: ((PsiElement) -> Boolean)?,
        forInlining: Boolean
    ): Result {
        val resolver = JKResolver(project, targetModule, contextElement)
        val symbolProvider = JKSymbolProvider(resolver)
        val typeFactory = JKTypeFactory(symbolProvider)
        symbolProvider.typeFactory = typeFactory
        symbolProvider.preBuildTree(inputElements)

        val languageVersion = when {
            contextElement.isPhysical -> contextElement.languageVersionSettings
            else -> LanguageVersionSettingsImpl.DEFAULT
        }

        val importStorage = JKImportStorage(languageVersion)
        val treeBuilder = JavaToJKTreeBuilder(symbolProvider, typeFactory, referenceSearcher, importStorage, bodyFilter, forInlining)

        // we want to leave all imports as is in the case when user is converting only imports
        val saveImports = inputElements.all { element ->
            element is PsiComment || element is PsiWhiteSpace
                    || element is PsiImportStatementBase || element is PsiImportList
                    || element is PsiPackageStatement
        }

        val elementsWithAsts = inputElements.mapIndexed { i, element ->
            processor.updateState(fileIndex = i, phase = BUILD_AST, phaseDescription)
            element to treeBuilder.buildTree(element, saveImports)
        }

        fun isInConversionContext(element: PsiElement): Boolean =
            inputElements.any { it == element || it.isAncestor(element, strict = true) }

        val externalCodeProcessing = NewExternalCodeProcessing(referenceSearcher, ::isInConversionContext)
        val context = NewJ2kConverterContext(
            symbolProvider,
            typeFactory,
            converter = this,
            ::isInConversionContext,
            importStorage,
            JKElementInfoStorage(),
            externalCodeProcessing,
            languageVersion.supportsFeature(LanguageFeature.FunctionalInterfaceConversion),
            settings
        )

        val treeRoots = elementsWithAsts.mapNotNull { it.second }
        ConversionsRunner.doApply(treeRoots, context) { conversionIndex, conversionCount, fileIndex, description ->
            processor.updateState(
                RUN_CONVERSIONS.phaseNumber,
                conversionIndex,
                conversionCount,
                fileIndex,
                description
            )
        }

        val results = elementsWithAsts.mapIndexed { i, elementWithAst ->
            processor.updateState(fileIndex = i, phase = PRINT_CODE, phaseDescription)
            val (element, ast) = elementWithAst
            if (ast == null) return@mapIndexed null
            val code = JKCodeBuilder(context).run { printCodeOut(ast) }
            val parseContext = when (element) {
                is PsiStatement, is PsiExpression -> ParseContext.CODE_BLOCK
                else -> ParseContext.TOP_LEVEL
            }
            ElementResult(
                code,
                importsToAdd = importStorage.getImports(),
                parseContext = parseContext
            )
        }

        return Result(
            results,
            externalCodeProcessing.takeIf { it.isExternalProcessingNeeded() },
            context
        )
    }

    override fun elementsToKotlin(inputElements: List<PsiElement>): Result {
        return elementsToKotlin(inputElements, NewJ2kWithProgressProcessor.DEFAULT)
    }

    override fun elementsToKotlin(inputElements: List<PsiElement>, processor: WithProgressProcessor): Result {
        return elementsToKotlin(inputElements, processor, null)
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

private fun WithProgressProcessor.updateState(fileIndex: Int?, phase: J2KConversionPhase, phaseDescription: String) {
    updateState(fileIndex, phase.phaseNumber, phaseDescription)
}

private enum class J2KConversionPhase(val phaseNumber: Int) {
    BUILD_AST(0),
    RUN_CONVERSIONS(1),
    PRINT_CODE(2),
    CREATE_FILES(3)
}