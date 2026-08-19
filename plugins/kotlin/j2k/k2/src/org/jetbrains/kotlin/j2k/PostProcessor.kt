// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.j2k

// import removed: forbidAnalysis no longer used during application phase
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.checkCanceled
import com.intellij.openapi.util.TextRange
import com.intellij.psi.codeStyle.CodeStyleManager
import org.jetbrains.kotlin.analysis.api.session.analyze
import org.jetbrains.kotlin.analysis.api.diagnostics.KaDiagnosticWithPsi
import org.jetbrains.kotlin.idea.base.psi.imports.addImport
import org.jetbrains.kotlin.j2k.PostProcessingTarget.MultipleFilesPostProcessingTarget
import org.jetbrains.kotlin.j2k.PostProcessingTarget.PieceOfCodePostProcessingTarget
import org.jetbrains.kotlin.j2k.postProcessings.FormatCodeProcessing
import org.jetbrains.kotlin.j2k.postProcessings.K2ConvertGettersAndSettersToPropertyProcessing
import org.jetbrains.kotlin.j2k.postProcessings.K2ShortenReferenceProcessing
import org.jetbrains.kotlin.j2k.postProcessings.MergePropertyWithConstructorParameterProcessing
import org.jetbrains.kotlin.j2k.postProcessings.OptimizeImportsProcessing
import org.jetbrains.kotlin.j2k.postProcessings.RemoveExplicitPropertyTypeProcessing
import org.jetbrains.kotlin.j2k.postProcessings.RemoveRedundantEmptyLinesProcessing
import org.jetbrains.kotlin.j2k.postProcessings.argumentTypeMismatchProcessing
import org.jetbrains.kotlin.j2k.postProcessings.assignmentTypeMismatchProcessing
import org.jetbrains.kotlin.j2k.postProcessings.initializerTypeMismatchProcessing
import org.jetbrains.kotlin.j2k.postProcessings.iteratorOnNullableProcessing
import org.jetbrains.kotlin.j2k.postProcessings.returnTypeMismatchProcessing
import org.jetbrains.kotlin.j2k.postProcessings.smartcastImpossibleProcessing
import org.jetbrains.kotlin.j2k.postProcessings.unnecessaryNotNullAssertionProcessing
import org.jetbrains.kotlin.j2k.postProcessings.unsafeCallProcessing
import org.jetbrains.kotlin.j2k.postProcessings.unsafeInfixCallProcessing
import org.jetbrains.kotlin.j2k.postProcessings.unsafeOperatorCallProcessing
import org.jetbrains.kotlin.j2k.postProcessings.uselessCastProcessing
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile

private val LOG = Logger.getInstance(PostProcessor::class.java)


object PostProcessor {
    val phasesCount: Int = processings.size

    suspend fun run(
        kotlinFile: KtFile,
        converterContext: ConverterContext? = null,
        range: TextRange? = null
    ) {
        val target =
            if (range != null) PieceOfCodePostProcessingTarget(kotlinFile, range.toRangeMarker(kotlinFile))
            else MultipleFilesPostProcessingTarget(listOf(kotlinFile))

        doAdditionalProcessing(target, converterContext)
    }

    private fun TextRange.toRangeMarker(file: KtFile): RangeMarker {
        val rangeMarker = runReadAction { file.viewProvider.document!!.createRangeMarker(startOffset, endOffset) }
        return rangeMarker.apply {
            isGreedyToLeft = true
            isGreedyToRight = true
        }
    }

    fun insertImport(file: KtFile, fqName: FqName) {
        runUndoTransparentActionInEdt(inWriteAction = true) {
            file.addImport(fqName)
        }
    }

    suspend fun doAdditionalProcessing(
        target: PostProcessingTarget,
        converterContext: ConverterContext?,
        onPhaseChanged: ((Int, String) -> Unit)? = null
    ) {
        if (converterContext == null) error("Invalid converter context for K2 J2K")
        val contextElement = target.files().firstOrNull() ?: return

        for ((i, group) in processings.withIndex()) {
            checkCanceled()
            onPhaseChanged?.invoke(i, group.description)
            val appliers = mutableListOf<PostProcessingApplier>()

            // Step 1: compute appliers
            readAction {
                analyze(contextElement) {
                    for (processing in group.processings) {
                        try {
                            appliers += processing.computeAppliers(target, converterContext)
                        } catch (e: ProcessCanceledException) {
                            throw e
                        } catch (t: Throwable) {
                            LOG.error(t)
                        }
                    }
                }
            }

            // Step 2: apply them
            for (applier in appliers) {
                checkCanceled()
                edtWriteAction {
                    CodeStyleManager.getInstance(contextElement.project).performActionWithFormatterDisabled {
                        try {
                            applier.apply()
                        } catch (e: ProcessCanceledException) {
                            throw e
                        } catch (t: Throwable) {
                            LOG.error(t)
                        }
                    }
                }
            }
        }
    }
}

// TODO try to reduce the number of post-processing groups for better performance
private val processings: List<NamedPostProcessingGroup> = listOf(
    NamedPostProcessingGroup(
        KotlinJ2kBundle.message("processing.step.cleaning.up.code"),
        listOf(
            K2ConvertGettersAndSettersToPropertyProcessing()
        ),
    ),

    NamedPostProcessingGroup(
        KotlinJ2kBundle.message("processing.step.cleaning.up.code"),
        listOf(
            MergePropertyWithConstructorParameterProcessing()
        ),
    ),

    NamedPostProcessingGroup(
        KotlinJ2kBundle.message("processing.step.cleaning.up.code"),
        listOf(
            @Suppress("UNCHECKED_CAST") // fighting with generics :(
            (K2DiagnosticBasedPostProcessingGroup(
                uselessCastProcessing as K2DiagnosticBasedProcessing<KaDiagnosticWithPsi<*>>,
                smartcastImpossibleProcessing as K2DiagnosticBasedProcessing<KaDiagnosticWithPsi<*>>,
                argumentTypeMismatchProcessing as K2DiagnosticBasedProcessing<KaDiagnosticWithPsi<*>>,
                returnTypeMismatchProcessing as K2DiagnosticBasedProcessing<KaDiagnosticWithPsi<*>>,
                assignmentTypeMismatchProcessing as K2DiagnosticBasedProcessing<KaDiagnosticWithPsi<*>>,
                initializerTypeMismatchProcessing as K2DiagnosticBasedProcessing<KaDiagnosticWithPsi<*>>,
                unsafeCallProcessing as K2DiagnosticBasedProcessing<KaDiagnosticWithPsi<*>>,
                unsafeInfixCallProcessing as K2DiagnosticBasedProcessing<KaDiagnosticWithPsi<*>>,
                unsafeOperatorCallProcessing as K2DiagnosticBasedProcessing<KaDiagnosticWithPsi<*>>,
                iteratorOnNullableProcessing as K2DiagnosticBasedProcessing<KaDiagnosticWithPsi<*>>,
            )),
            InspectionLikeProcessingGroup(
                RemoveExplicitPropertyTypeProcessing(),
            ),
            K2ShortenReferenceProcessing(),
            RemoveRedundantEmptyLinesProcessing(),
            FormatCodeProcessing()
        )
    ),

    NamedPostProcessingGroup(
        KotlinJ2kBundle.message("processing.step.optimizing.imports"),
        listOf(
            @Suppress("UNCHECKED_CAST")
            (K2DiagnosticBasedPostProcessingGroup(
                unnecessaryNotNullAssertionProcessing as K2DiagnosticBasedProcessing<KaDiagnosticWithPsi<*>>,
            )),

            // OptimizeImportsProcessing depends on the results of K2ShortenReferenceProcessing,
            // that's why it currently has to be in a separate group: KTIJ-29644
            OptimizeImportsProcessing()
        )
    ),

    NamedPostProcessingGroup(
        KotlinJ2kBundle.message("processing.step.optimizing.imports"),
        listOf(
            FormatCodeProcessing()
        )
    )
)
