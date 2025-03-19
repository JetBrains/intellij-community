// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.j2k.post.processing

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import org.jetbrains.kotlin.idea.caches.resolve.resolveImportReference
import org.jetbrains.kotlin.idea.codeinsight.utils.commitAndUnblockDocument
import org.jetbrains.kotlin.idea.util.ImportInsertHelper
import org.jetbrains.kotlin.j2k.*
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile

class NewJ2kPostProcessor : PostProcessor {
    companion object {
        private val LOG = Logger.getInstance("@org.jetbrains.kotlin.idea.j2k.post.processings.NewJ2kPostProcessor")
    }

    override fun insertImport(file: KtFile, fqName: FqName) {
        runUndoTransparentActionInEdt(inWriteAction = true) {
            val descriptors = file.resolveImportReference(fqName)
            descriptors.firstOrNull()?.let { ImportInsertHelper.getInstance(file.project).importDescriptor(file, it) }
        }
    }

    override val phasesCount = allProcessings.size

    override fun doAdditionalProcessing(
        target: PostProcessingTarget,
        converterContext: ConverterContext?,
        onPhaseChanged: ((Int, String) -> Unit)?
    ) {
        if (converterContext !is ConverterContext) error("Invalid converter context for new J2K")

        for ((i, group) in allProcessings.withIndex()) {
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