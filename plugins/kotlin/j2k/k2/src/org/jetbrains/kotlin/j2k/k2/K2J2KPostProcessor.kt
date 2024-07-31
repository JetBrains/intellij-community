// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.j2k.k2

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.diagnostics.KaDiagnosticWithPsi
import org.jetbrains.kotlin.analysis.api.permissions.forbidAnalysis
import org.jetbrains.kotlin.j2k.*
import org.jetbrains.kotlin.j2k.k2.postProcessings.*
import org.jetbrains.kotlin.j2k.postProcessings.*
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.nj2k.NewJ2kConverterContext
import org.jetbrains.kotlin.nj2k.runUndoTransparentActionInEdt
import org.jetbrains.kotlin.psi.KtFile

internal class K2J2KPostProcessor : PostProcessor {
    companion object {
        private val LOG = Logger.getInstance(/* category = */ "@org.jetbrains.kotlin.j2k.K2J2KPostProcessor")
    }

    override val phasesCount: Int = processings.size

    override fun insertImport(file: KtFile, fqName: FqName) {
        TODO("Not supported in K2 J2K yet")
    }

    override fun doAdditionalProcessing(
        target: PostProcessingTarget,
        converterContext: ConverterContext?,
        onPhaseChanged: ((Int, String) -> Unit)?
    ) {
        if (converterContext !is NewJ2kConverterContext) error("Invalid converter context for K2 J2K")
        val contextElement = target.files().firstOrNull() ?: return

        // Run analysis and apply the post-processings for each group separately
        // so that later groups can depend on the results of earlier groups
        for ((i, group) in processings.withIndex()) {
            ProgressManager.checkCanceled()
            onPhaseChanged?.invoke(i, group.description)
            val appliers = mutableListOf<PostProcessingApplier>()

            // Step 1: compute appliers
            runReadAction {
                analyze(contextElement) {
                    for (processing in group.processings) {
                        ProgressManager.checkCanceled()
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
            forbidAnalysis("Apply J2K post-processings") {
                for (applier in appliers) {
                    ProgressManager.checkCanceled()
                    runUndoTransparentActionInEdt(inWriteAction = true) {
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
        KotlinJ2KK2Bundle.message("processing.step.cleaning.up.code"),
        listOf(
            K2ConvertGettersAndSettersToPropertyProcessing()
        ),
    ),

    NamedPostProcessingGroup(
        KotlinJ2KK2Bundle.message("processing.step.cleaning.up.code"),
        listOf(
            MergePropertyWithConstructorParameterProcessing()
        ),
    ),

    NamedPostProcessingGroup(
        KotlinJ2KK2Bundle.message("processing.step.cleaning.up.code"),
        listOf(
            @Suppress("UNCHECKED_CAST") // fighting with generics :(
            K2DiagnosticBasedPostProcessingGroup(
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
            ),
            InspectionLikeProcessingGroup(
                VarToValProcessing(),
                RemoveExplicitPropertyTypeProcessing(),
            ),
            K2ShortenReferenceProcessing(),
            RemoveRedundantEmptyLinesProcessing(),
            FormatCodeProcessing()
        )
    ),

    NamedPostProcessingGroup(
        KotlinJ2KK2Bundle.message("processing.step.optimizing.imports"),
        listOf(
            @Suppress("UNCHECKED_CAST")
            K2DiagnosticBasedPostProcessingGroup(
                unnecessaryNotNullAssertionProcessing as K2DiagnosticBasedProcessing<KaDiagnosticWithPsi<*>>,
            ),

            // OptimizeImportsProcessing depends on the results of K2ShortenReferenceProcessing,
            // that's why it currently has to be in a separate group: KTIJ-29644
            OptimizeImportsProcessing()
        )
    )
)
