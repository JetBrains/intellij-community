// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.j2k

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.util.Computable
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiJavaFile
import org.jetbrains.kotlin.K1Deprecation
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.j2k.ast.Element
import org.jetbrains.kotlin.j2k.usageProcessing.UsageProcessing
import org.jetbrains.kotlin.nj2k.externalCodeProcessing.ExternalUsagesFixer
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.isAncestor

@K1Deprecation
class OldJavaToKotlinConverter(
    private val settings: ConverterSettings
) : JavaToKotlinConverter() {
    private val referenceSearcher: ReferenceSearcher = IdeaReferenceSearcher

    companion object {
        private val LOG = Logger.getInstance(JavaToKotlinConverter::class.java)
    }

    /**
     * Preprocessor and postprocessor extensions are only handled in [NewJavaToKotlinConverter]. Any passed in here will be ignored.
     */
    override suspend fun filesToKotlin(
        files: List<PsiJavaFile>,
        postProcessor: PostProcessor,
        bodyFilter: ((PsiElement) -> Boolean)?,
        preprocessorExtensions: List<J2kPreprocessorExtension>,
        postprocessorExtensions: List<J2kPostprocessorExtension>,
    ): ConversionResult {
        val withProgressProcessor = OldWithProgressProcessor(null, files)
        val (results, externalCodeProcessing) = ApplicationManager.getApplication().runReadAction(Computable {
            elementsToKotlin(files, withProgressProcessor)
        })


        val texts = withProgressProcessor.processItems(0.5, results.withIndex()) { pair ->
            val (i, result) = pair
            try {
                val kotlinFile = ApplicationManager.getApplication().runReadAction(Computable {
                    KtPsiFactory.contextual(files[i]).createFile("dummy.kt", result!!.text)
                })

                result!!.importsToAdd.forEach { postProcessor.insertImport(kotlinFile, it) }

                J2KPostProcessingRunner.run(postProcessor, kotlinFile)

                kotlinFile.text
            } catch (e: ProcessCanceledException) {
                throw e
            } catch (t: Throwable) {
                LOG.error(t)
                result!!.text
            }
        }

        return ConversionResult(files.zip(texts).toMap(), externalCodeProcessing)
    }

    override fun elementsToKotlin(inputElements: List<PsiElement>, processor: WithProgressProcessor): Result {
        try {
            val usageProcessings = LinkedHashMap<PsiElement, MutableCollection<UsageProcessing>>()
            val usageProcessingCollector: (UsageProcessing) -> Unit = {
                usageProcessings.getOrPut(it.targetElement) { ArrayList() }.add(it)
            }

            fun inConversionScope(element: PsiElement) = inputElements.any { it.isAncestor(element, strict = false) }


            val intermediateResults = processor.processItems(0.25, inputElements) { inputElement ->
                Converter.create(inputElement, settings, referenceSearcher, ::inConversionScope, usageProcessingCollector).convert()
            }.toMutableList()

            val results = processor.processItems(0.25, intermediateResults.withIndex()) { pair ->
                val (i, result) = pair
                intermediateResults[i] = null // to not hold unused objects in the heap
                result?.let {
                    val (text, importsToAdd) = it.codeGenerator(usageProcessings)
                    ElementResult(text, importsToAdd, it.parseContext)
                }
            }

            val externalCodeProcessing = buildExternalCodeProcessing(usageProcessings)

            return Result(results, externalCodeProcessing, null)
        } catch (e: ElementCreationStackTraceRequiredException) {
            // if we got this exception then we need to turn element creation stack traces on to get better diagnostic
            Element.saveCreationStacktraces = true
            try {
                return elementsToKotlin(inputElements)
            } finally {
                Element.saveCreationStacktraces = false
            }
        }
    }

    override fun elementsToKotlin(inputElements: List<PsiElement>): Result {
        return elementsToKotlin(inputElements, OldWithProgressProcessor.DEFAULT)
    }


    private fun buildExternalCodeProcessing(
        usageProcessings: Map<PsiElement, Collection<UsageProcessing>>
    ): ExternalCodeProcessing? {
        if (usageProcessings.isEmpty()) return null

        val map: Map<PsiElement, Collection<UsageProcessing>> = usageProcessings.values
            .flatten()
            .filter { it.javaCodeProcessors.isNotEmpty() || it.kotlinCodeProcessors.isNotEmpty() }
            .groupBy { it.targetElement }
        if (map.isEmpty()) return null

        return object : ExternalCodeProcessing {
            context(_: KaSession)
            override fun bindJavaDeclarationsToConvertedKotlinOnes(files: List<KtFile>) {
                // Do nothing in Old J2K
            }

            override fun collectUsages(): List<ExternalUsagesFixer.JKMemberInfoWithUsages> {
                return emptyList()
            }
        }
    }

}
