// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.j2k

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import org.jetbrains.kotlin.idea.codeinsight.utils.commitAndUnblockDocument
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

        for ((i, group) in processings.withIndex()) {
            ProgressManager.checkCanceled()
            onPhaseChanged?.invoke(i, group.description)
            for (processing in group.processings) {
                ProgressManager.checkCanceled()
                try {
                    processing.runProcessingConsideringOptions(target, converterContext)
                    target.files().forEach(::commitFile)
                } catch (e: ProcessCanceledException) {
                    throw e
                } catch (t: Throwable) {
                    target.files().forEach(::commitFile)
                    LOG.error(t)
                }
            }
        }
    }

    private fun commitFile(file: KtFile) {
        runUndoTransparentActionInEdt(inWriteAction = true) {
            file.commitAndUnblockDocument()
        }
    }
}

private val processings: List<NamedPostProcessingGroup> = listOf(
    NamedPostProcessingGroup(
        KotlinJ2KK2Bundle.message("processing.step.cleaning.up.code"),
        listOf(
            InspectionLikeProcessingGroup(
                runSingleTime = true,
                listOf(
                    RemoveExplicitPropertyTypeProcessing(),
                )
            ),
        )
    ),
    NamedPostProcessingGroup(
        KotlinJ2KK2Bundle.message("processing.step.optimizing.imports.and.formatting.code"),
        listOf(
            K2ShortenReferenceProcessing(),
            OptimizeImportsProcessing(),
            RemoveRedundantEmptyLinesProcessing(),
            FormatCodeProcessing()
        )
    )
)
