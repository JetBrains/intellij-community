// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.psi.*
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.config.LanguageVersionSettingsImpl
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.j2k.*
import org.jetbrains.kotlin.name.FqName
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
    val oldConverterServices: JavaToKotlinConverterServices
) : JavaToKotlinConverter() {
    val converterServices = object : NewJavaToKotlinServices {
        override val oldServices = oldConverterServices
    }

    val phasesCount = J2KConversionPhase.values().size

    override fun filesToKotlin(
        files: List<PsiJavaFile>,
        postProcessor: PostProcessor,
        progress: ProgressIndicator
    ): FilesResult = filesToKotlin(files, postProcessor, progress, null)

    fun filesToKotlin(
        files: List<PsiJavaFile>,
        postProcessor: PostProcessor,
        progress: ProgressIndicator,
        bodyFilter: ((PsiElement) -> Boolean)?,
    ): FilesResult {
        val withProgressProcessor = NewJ2kWithProgressProcessor(progress, files, postProcessor.phasesCount + phasesCount)
        return withProgressProcessor.process {
            val (results, externalCodeProcessing, context) =
                ApplicationManager.getApplication().runReadAction(Computable {
                    elementsToKotlin(files, withProgressProcessor, bodyFilter)
                })

            val kotlinFiles = results.mapIndexed { i, result ->
                runUndoTransparentActionInEdt(inWriteAction = true) {
                    val javaFile = files[i]
                    withProgressProcessor.updateState(
                        fileIndex = i,
                        phase = J2KConversionPhase.CREATE_FILES,
                        description = "Creating files..."
                    )
                    KtPsiFactory.contextual(files[i]).createPhysicalFile(javaFile.name.replace(".java", ".kt"), result!!.text)
                        .also { it.addImports(result.importsToAdd) }
                }

            }

            postProcessor.doAdditionalProcessing(
                JKMultipleFilesPostProcessingTarget(kotlinFiles),
                context
            ) { phase, description ->
                withProgressProcessor.updateState(fileIndex = null, phase = phase + phasesCount, description = description)
            }
            FilesResult(kotlinFiles.map { it.text }, externalCodeProcessing)
        }
    }

    fun elementsToKotlin(
        inputElements: List<PsiElement>,
        processor: WithProgressProcessor,
        bodyFilter: ((PsiElement) -> Boolean)?
    ): Result {
        val phaseDescription = KotlinNJ2KBundle.message("phase.converting.j2k")
        val contextElement = inputElements.firstOrNull() ?: return Result(emptyList(), null, null)
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
        val treeBuilder = JavaToJKTreeBuilder(symbolProvider, typeFactory, converterServices, importStorage, bodyFilter)

        // we want to leave all imports as is in the case when user is converting only imports
        val saveImports = inputElements.all { element ->
            element is PsiComment || element is PsiWhiteSpace
                    || element is PsiImportStatementBase || element is PsiImportList
                    || element is PsiPackageStatement
        }

        val asts = inputElements.mapIndexed { i, element ->
            processor.updateState(i, J2KConversionPhase.BUILD_AST, phaseDescription)
            element to treeBuilder.buildTree(element, saveImports)
        }
        val inConversionContext = { element: PsiElement ->
            inputElements.any { inputElement ->
                if (inputElement == element) return@any true
                inputElement.isAncestor(element, true)
            }
        }

        val externalCodeProcessing =
            NewExternalCodeProcessing(oldConverterServices.referenceSearcher, inConversionContext)

        val context = NewJ2kConverterContext(
            symbolProvider,
            typeFactory,
            this,
            inConversionContext,
            importStorage,
            JKElementInfoStorage(),
            externalCodeProcessing,
            languageVersion.supportsFeature(LanguageFeature.FunctionalInterfaceConversion)
        )
        ConversionsRunner.doApply(asts.mapNotNull { it.second }, context) { conversionIndex, conversionCount, i, desc ->
            processor.updateState(
                J2KConversionPhase.RUN_CONVERSIONS.phaseNumber,
                conversionIndex,
                conversionCount,
                i,
                desc
            )
        }

        val results = asts.mapIndexed { i, elementWithAst ->
            processor.updateState(i, J2KConversionPhase.PRINT_CODE, phaseDescription)
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
            val psiFactory = KtPsiFactory(project)


            if (imports.isEmpty()) return
            val importPsi = psiFactory.createImportDirectives(
                imports.map { ImportPath(it, isAllUnder = false) }
            )
            val createdImportList = importPsi.first().parent as KtImportList
            val importList = importList
            if (importList == null) {
                addImportList(createdImportList)
            } else {
                val updatedList = if (importList.firstChild != null) {
                    createdImportList.addRangeBefore(importList.firstChild, importList.lastChild, createdImportList.firstChild)
                } else createdImportList
                importList.replace(updatedList)
            }
        }

        private fun KtFile.addImportList(importList: KtImportList): KtImportList {
            if (packageDirective != null) {
                return addAfter(importList, packageDirective) as KtImportList
            }

            val firstDeclaration = findChildByClass(KtDeclaration::class.java)
            if (firstDeclaration != null) {
                return addBefore(importList, firstDeclaration) as KtImportList
            }

            return add(importList) as KtImportList
        }
    }
}

class NewJ2kWithProgressProcessor(
    private val progress: ProgressIndicator?,
    private val files: List<PsiJavaFile>?,
    private val phasesCount: Int
) : WithProgressProcessor {
    companion object {
        val DEFAULT = NewJ2kWithProgressProcessor(null, null, 0)
    }

    init {
        progress?.isIndeterminate = false
    }

    override fun updateState(fileIndex: Int?, phase: Int, description: String) {
        if (fileIndex == null)
            updateState(phase, 1, 1, null, description)
        else
            updateState(phase, 0, 1, fileIndex, description)
    }

    override fun updateState(
        phase: Int,
        subPhase: Int,
        subPhaseCount: Int,
        fileIndex: Int?,
        description: String
    ) {
        ProgressManager.checkCanceled()
        progress?.checkCanceled()
        val singlePhaseFraction = 1.0 / phasesCount.toDouble()
        val singleSubPhaseFraction = singlePhaseFraction / subPhaseCount.toDouble()

        var resultFraction = phase * singlePhaseFraction + subPhase * singleSubPhaseFraction
        if (files != null && fileIndex != null && files.isNotEmpty()) {
            val fileFraction = singleSubPhaseFraction / files.size.toDouble()
            resultFraction += fileFraction * fileIndex
        }
        progress?.fraction = resultFraction

        if (subPhaseCount > 1) {
            progress?.text = KotlinNJ2KBundle.message(
                "subphase.progress.text",
                description,
                subPhase,
                subPhaseCount,
                phase + 1,
                phasesCount
            )
        } else {
            progress?.text = KotlinNJ2KBundle.message("progress.text", description, phase + 1, phasesCount)
        }
        progress?.text2 = when {
            !files.isNullOrEmpty() && fileIndex != null -> files[fileIndex].virtualFile.presentableUrl + if (files.size > 1) " ($fileIndex/${files.size})" else ""
            else -> ""
        }
    }

    override fun <TInputItem, TOutputItem> processItems(
        fractionPortion: Double,
        inputItems: Iterable<TInputItem>,
        processItem: (TInputItem) -> TOutputItem
    ): List<TOutputItem> {
        throw AbstractMethodError("Should not be called for new J2K")
    }

    override fun <T> process(action: () -> T): T = action()

}

internal fun WithProgressProcessor.updateState(fileIndex: Int?, phase: J2KConversionPhase, description: String) {
    updateState(fileIndex, phase.phaseNumber, description)
}

internal enum class J2KConversionPhase(val phaseNumber: Int) {
    BUILD_AST(0),
    RUN_CONVERSIONS(1),
    PRINT_CODE(2),
    CREATE_FILES(3)
}