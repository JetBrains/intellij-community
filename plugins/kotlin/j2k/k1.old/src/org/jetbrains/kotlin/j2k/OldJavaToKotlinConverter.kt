// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.j2k

import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.psi.*
import com.intellij.psi.impl.source.DummyHolder
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.j2k.ast.Element
import org.jetbrains.kotlin.j2k.usageProcessing.ExternalCodeProcessor
import org.jetbrains.kotlin.j2k.usageProcessing.UsageProcessing
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.isAncestor
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf

class OldJavaToKotlinConverter(
    private val project: Project,
    private val settings: ConverterSettings
) : JavaToKotlinConverter() {
    private val referenceSearcher: ReferenceSearcher = IdeaReferenceSearcher

    companion object {
        private val LOG = Logger.getInstance(JavaToKotlinConverter::class.java)
    }

    /**
     * Preprocessor and postprocessor extensions are only handled in [NewJavaToKotlinConverter]. Any passed in here will be ignored.
     */
    override fun filesToKotlin(
        files: List<PsiJavaFile>,
        postProcessor: PostProcessor,
        progressIndicator: ProgressIndicator,
        preprocessorExtensions: List<J2kPreprocessorExtension>,
        postprocessorExtensions: List<J2kPostprocessorExtension>
    ): FilesResult {
        val withProgressProcessor = OldWithProgressProcessor(progressIndicator, files)
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

        return FilesResult(texts, externalCodeProcessing)
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

            val externalCodeProcessing = buildExternalCodeProcessing(usageProcessings, ::inConversionScope)

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


    private data class ReferenceInfo(
        val reference: PsiReference,
        val target: PsiElement,
        val file: PsiFile,
        val processings: Collection<UsageProcessing>
    ) {
        val depth: Int by lazy(LazyThreadSafetyMode.NONE) { target.parentsWithSelf.takeWhile { it !is PsiFile }.count() }
        val offset: Int by lazy(LazyThreadSafetyMode.NONE) { reference.element.textRange.startOffset }
    }

    private fun buildExternalCodeProcessing(
        usageProcessings: Map<PsiElement, Collection<UsageProcessing>>,
        inConversionScope: (PsiElement) -> Boolean
    ): ExternalCodeProcessing? {
        if (usageProcessings.isEmpty()) return null

        val map: Map<PsiElement, Collection<UsageProcessing>> = usageProcessings.values
            .flatten()
            .filter { it.javaCodeProcessors.isNotEmpty() || it.kotlinCodeProcessors.isNotEmpty() }
            .groupBy { it.targetElement }
        if (map.isEmpty()) return null

        return object : ExternalCodeProcessing {
            override fun prepareWriteOperation(progress: ProgressIndicator?): () -> Unit {
                if (progress == null) error("Progress should not be null for old J2K")
                val refs = ArrayList<ReferenceInfo>()

                progress.text = KotlinJ2KBundle.message("text.searching.usages.to.update")

                for ((i, entry) in map.entries.withIndex()) {
                    val psiElement = entry.key
                    val processings = entry.value

                    progress.text2 = (psiElement as? PsiNamedElement)?.name ?: ""
                    progress.checkCanceled()

                    ProgressManager.getInstance().runProcess(
                        {
                            val searchJava = processings.any { it.javaCodeProcessors.isNotEmpty() }
                            val searchKotlin = processings.any { it.kotlinCodeProcessors.isNotEmpty() }
                            referenceSearcher.findUsagesForExternalCodeProcessing(psiElement, searchJava, searchKotlin)
                                .filterNot { inConversionScope(it.element) }
                                .mapTo(refs) { ReferenceInfo(it, psiElement, it.element.containingFile, processings) }
                        },
                        ProgressPortionReporter(progress, i / map.size.toDouble(), 1.0 / map.size)
                    )

                }

                return { processUsages(refs) }
            }

            context(_: KaSession)
            override fun bindJavaDeclarationsToConvertedKotlinOnes(files: List<KtFile>) {
                // Do nothing in Old J2K
            }
        }
    }

    private fun processUsages(refs: Collection<ReferenceInfo>) {
        for (fileRefs in refs.groupBy { it.file }.values) { // group by file for faster sorting
            ReferenceLoop@
            for ((reference, _, _, processings) in fileRefs.sortedWith(ReferenceComparator)) {
                val processors = when (reference.element.language) {
                    JavaLanguage.INSTANCE -> processings.flatMap { it.javaCodeProcessors }
                    KotlinLanguage.INSTANCE -> processings.flatMap { it.kotlinCodeProcessors }
                    else -> continue@ReferenceLoop
                }

                checkReferenceValid(reference, null)

                var references = listOf(reference)
                for (processor in processors) {
                    references = references.flatMap { processor.processUsage(it)?.toList() ?: listOf(it) }
                    references.forEach { checkReferenceValid(it, processor) }
                }
            }
        }
    }

    private fun checkReferenceValid(reference: PsiReference, afterProcessor: ExternalCodeProcessor?) {
        val element = reference.element
        assert(element.isValid && element.containingFile !is DummyHolder) {
            "Reference $reference got invalidated" + (if (afterProcessor != null) " after processing with $afterProcessor" else "")
        }
    }

    private object ReferenceComparator : Comparator<ReferenceInfo> {
        override fun compare(info1: ReferenceInfo, info2: ReferenceInfo): Int {
            val depth1 = info1.depth
            val depth2 = info2.depth
            if (depth1 != depth2) { // put deeper elements first to not invalidate them when processing ancestors
                return -depth1.compareTo(depth2)
            }

            // process elements with the same deepness from right to left so that right-side of assignments is not invalidated by processing of the left one
            return -info1.offset.compareTo(info2.offset)
        }
    }
}
