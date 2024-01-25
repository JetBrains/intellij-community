// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.psi.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.config.LanguageVersionSettingsImpl
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.j2k.*
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

@ApiStatus.Internal
class NewJavaToKotlinConverter(
    val project: Project,
    val targetModule: Module?,
    val settings: ConverterSettings
) : JavaToKotlinConverter() {
    val phasesCount = J2KConversionPhase.entries.size
    val referenceSearcher: ReferenceSearcher = IdeaReferenceSearcher

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
                    withProgressProcessor.updateState(fileIndex = i, phase = CREATE_FILES)
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
        bodyFilter: ((PsiElement) -> Boolean)?,
        forInlining: Boolean = false
    ): Result {
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
        val treeBuilder = JavaToJKTreeBuilder(symbolProvider, typeFactory, referenceSearcher, importStorage, bodyFilter, forInlining)

        // we want to leave all imports as is in the case when user is converting only imports
        val saveImports = inputElements.all { element ->
            element is PsiComment || element is PsiWhiteSpace
                    || element is PsiImportStatementBase || element is PsiImportList
                    || element is PsiPackageStatement
        }

        val asts = inputElements.mapIndexed { i, element ->
            processor.updateState(fileIndex = i, phase = BUILD_AST)
            element to treeBuilder.buildTree(element, saveImports)
        }
        val inConversionContext = { element: PsiElement ->
            inputElements.any { inputElement ->
                if (inputElement == element) return@any true
                inputElement.isAncestor(element, true)
            }
        }

        val externalCodeProcessing = NewExternalCodeProcessing(referenceSearcher, inConversionContext)

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
                RUN_CONVERSIONS.phaseNumber,
                conversionIndex,
                conversionCount,
                i,
                desc
            )
        }

        val results = asts.mapIndexed { i, elementWithAst ->
            processor.updateState(fileIndex = i, phase = PRINT_CODE)
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
                addImportList(createdImportList)
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
            if (firstDeclaration != null) {
                return addBefore(importList, firstDeclaration) as KtImportList
            }

            return add(importList) as KtImportList
        }

        private fun PsiElement.ensureLineBreaksAfter(psiFactory: KtPsiFactory) {
            val nextWhiteSpace = (nextSibling as? PsiWhiteSpace)?.text ?: return
            val numberOfNewLinesToAdd = when {
                nextWhiteSpace.startsWith("\n\n") -> return
                nextWhiteSpace.startsWith("\n") -> 1
                else -> 2
            }
            parent?.addAfter(psiFactory.createNewLine(numberOfNewLinesToAdd), /* anchor = */ this)
        }
    }
}

private val phaseDescription: String = KotlinNJ2KBundle.message("phase.converting.j2k")

private fun WithProgressProcessor.updateState(fileIndex: Int?, phase: J2KConversionPhase) {
    updateState(fileIndex, phase.phaseNumber, phaseDescription)
}

private enum class J2KConversionPhase(val phaseNumber: Int) {
    BUILD_AST(0),
    RUN_CONVERSIONS(1),
    PRINT_CODE(2),
    CREATE_FILES(3)
}