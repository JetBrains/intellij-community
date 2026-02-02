// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k

import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.platform.ide.progress.withModalProgress
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiImportList
import com.intellij.psi.PsiImportStatementBase
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiPackageStatement
import com.intellij.psi.PsiStatement
import com.intellij.psi.PsiWhiteSpace
import org.jetbrains.kotlin.analysis.api.projectStructure.KaSourceModule
import org.jetbrains.kotlin.config.LanguageVersionSettingsImpl
import org.jetbrains.kotlin.idea.actions.withCommandOnEdt
import org.jetbrains.kotlin.idea.base.projectStructure.getKaModuleOfTypeSafe
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.projectStructure.toKaSourceModuleWithElementSourceModuleKindOrProduction
import org.jetbrains.kotlin.j2k.ConverterContext
import org.jetbrains.kotlin.j2k.ConverterSettings
import org.jetbrains.kotlin.j2k.ConvertionResult
import org.jetbrains.kotlin.j2k.ElementResult
import org.jetbrains.kotlin.j2k.IdeaReferenceSearcher
import org.jetbrains.kotlin.j2k.J2kPostprocessorExtension
import org.jetbrains.kotlin.j2k.J2kPreprocessorExtension
import org.jetbrains.kotlin.j2k.JavaToKotlinConverter
import org.jetbrains.kotlin.j2k.ParseContext.CODE_BLOCK
import org.jetbrains.kotlin.j2k.ParseContext.TOP_LEVEL
import org.jetbrains.kotlin.j2k.PostProcessingTarget.MultipleFilesPostProcessingTarget
import org.jetbrains.kotlin.j2k.PostProcessor
import org.jetbrains.kotlin.j2k.ReferenceSearcher
import org.jetbrains.kotlin.j2k.Result
import org.jetbrains.kotlin.j2k.WithProgressProcessor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.nj2k.externalCodeProcessing.NewExternalCodeProcessing
import org.jetbrains.kotlin.nj2k.printing.JKCodeBuilder
import org.jetbrains.kotlin.nj2k.types.JKTypeFactory
import org.jetbrains.kotlin.platform.TargetPlatform
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
    val phasesCount: Int = J2KConversionPhase.entries.size
    val referenceSearcher: ReferenceSearcher = IdeaReferenceSearcher
    private val phaseDescription: String = KotlinNJ2KBundle.message("j2k.phase.converting")

    override suspend fun filesToKotlin(
        files: List<PsiJavaFile>,
        postProcessor: PostProcessor,
        bodyFilter: ((PsiElement) -> Boolean)?,
        preprocessorExtensions: List<J2kPreprocessorExtension>,
        postprocessorExtensions: List<J2kPostprocessorExtension>
    ): ConvertionResult = withModalProgress(project, phaseDescription) {
        withCommandOnEdt(project) {
            PreprocessorExtensionsRunner.runProcessors(project, files, preprocessorExtensions)

            val (results, externalCodeProcessing, context) = readAction {
                elementsToKotlin(files, bodyFilter)
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

            ConvertionResult(files.zip(kotlinFiles.map { it.text }).toMap(), externalCodeProcessing, javaLines, kotlinLines)
        }
    }

    fun elementsToKotlin(
        inputElements: List<PsiElement>,
        bodyFilter: ((PsiElement) -> Boolean)?,
        forInlining: Boolean = false
    ): Result {
        val contextElement = inputElements.firstOrNull() ?: return Result.EMPTY
        val targetKaModule =
            targetFile?.getKaModuleOfTypeSafe<KaSourceModule>(project, useSiteModule = null)
            // This `KaSourceModule` is not 100% waterproof, but without the target file, we don't actually know the kind of the target
            // source module. The most reasonable assumption is that we copy the input element to a source module of the same kind.
                ?: targetModule?.toKaSourceModuleWithElementSourceModuleKindOrProduction(contextElement)
                ?: return Result.EMPTY
        val targetPlatform = targetKaModule.targetPlatform
        return doConvertElementsToKotlin(
            contextElement = contextElement,
            inputElements = inputElements,
            targetPlatform = targetPlatform,
            bodyFilter = bodyFilter,
            forInlining = forInlining
        )
    }

    private fun doConvertElementsToKotlin(
        contextElement: PsiElement,
        inputElements: List<PsiElement>,
        targetPlatform: TargetPlatform,
        bodyFilter: ((PsiElement) -> Boolean)?,
        forInlining: Boolean
    ): Result {
        val resolver = JKResolver(project, targetModule, contextElement)
        val symbolProvider = JKSymbolProvider(resolver)
        val typeFactory = JKTypeFactory(symbolProvider)
        symbolProvider.typeFactory = typeFactory
        symbolProvider.preBuildTree(inputElements)

        val languageVersionSettings = when {
            contextElement.isPhysical -> contextElement.languageVersionSettings
            else -> LanguageVersionSettingsImpl.DEFAULT
        }

        val importStorage = JKImportStorage(targetPlatform, project)
        val treeBuilder = JavaToJKTreeBuilder(symbolProvider, typeFactory, referenceSearcher, importStorage, bodyFilter, forInlining)

        // we want to leave all imports as is in the case when user is converting only imports
        val saveImports = inputElements.all { element ->
            element is PsiComment || element is PsiWhiteSpace
                    || element is PsiImportStatementBase || element is PsiImportList
                    || element is PsiPackageStatement
        }

        val elementsWithAsts = inputElements.associateWith { treeBuilder.buildTree(it, saveImports) }

        fun isInConversionContext(element: PsiElement): Boolean =
            inputElements.any { it == element || it.isAncestor(element, strict = true) }

        val externalCodeProcessing = NewExternalCodeProcessing(referenceSearcher, ::isInConversionContext)
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

    override fun elementsToKotlin(inputElements: List<PsiElement>): Result {
        return elementsToKotlin(inputElements, NewJ2kWithProgressProcessor.DEFAULT)
    }

    override fun elementsToKotlin(inputElements: List<PsiElement>, processor: WithProgressProcessor): Result {
        return elementsToKotlin(inputElements, null)
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

private enum class J2KConversionPhase() {
    PREPROCESSING(),
    BUILD_AST(),
    RUN_CONVERSIONS(),
    PRINT_CODE(),
    CREATE_FILES()
}
