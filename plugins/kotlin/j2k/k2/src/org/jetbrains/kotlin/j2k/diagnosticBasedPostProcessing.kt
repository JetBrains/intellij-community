// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.j2k

import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.editor.asTextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.createSmartPointer
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.components.KtDiagnosticCheckerFilter.ONLY_COMMON_CHECKERS
import org.jetbrains.kotlin.analysis.api.diagnostics.KtDiagnosticWithPsi
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.nj2k.NewJ2kConverterContext
import org.jetbrains.kotlin.psi.KtFile
import kotlin.reflect.KClass

internal class K2DiagnosticBasedPostProcessingGroup(
    vararg diagnosticBasedProcessings: K2DiagnosticBasedProcessing<KtDiagnosticWithPsi<*>>
) : FileBasedPostProcessing() {

    private val diagnosticToProcessing: Map<KClass<out KtDiagnosticWithPsi<*>>, K2DiagnosticBasedProcessing<KtDiagnosticWithPsi<*>>> =
        diagnosticBasedProcessings.associateBy({ it.diagnosticClass }, { it })

    override fun runProcessing(file: KtFile, allFiles: List<KtFile>, rangeMarker: RangeMarker?, converterContext: NewJ2kConverterContext) {
        error("Not supported in K2 J2K")
    }

    context(KtAnalysisSession)
    override fun computeApplier(
        file: KtFile,
        allFiles: List<KtFile>,
        rangeMarker: RangeMarker?,
        converterContext: NewJ2kConverterContext
    ): PostProcessingApplier {
        // TODO: for copy-paste conversion, try to restrict the analysis range, like in K1 J2K
        //  (see org.jetbrains.kotlin.idea.j2k.post.processing.DiagnosticBasedPostProcessingGroup.analyzeFileRange)
        val diagnostics = file.collectDiagnosticsForFile(filter = ONLY_COMMON_CHECKERS)
        val processingDataList = mutableListOf<ProcessingData>()

        for (diagnostic in diagnostics) {
            val processingData = processDiagnostic(diagnostic, file, rangeMarker)
            if (processingData != null) {
                processingDataList += processingData
            }
        }

        return Applier(processingDataList)
    }

    context(KtAnalysisSession)
    private fun processDiagnostic(diagnostic: KtDiagnosticWithPsi<*>, file: KtFile, rangeMarker: RangeMarker?): ProcessingData? {
        val processing = diagnosticToProcessing[diagnostic.diagnosticClass] ?: return null
        val element = diagnostic.psi
        val range = rangeMarker?.asTextRange ?: file.textRange

        if (!range.contains(element.textRange)) return null

        val fix = processing.createFix(diagnostic) ?: return null
        return ProcessingData(fix, element.createSmartPointer())
    }

    private class Applier(private val processingDataList: List<ProcessingData>) : PostProcessingApplier {
        override fun apply() {
            for ((fix, pointer) in processingDataList) {
                val element = pointer.element ?: continue
                fix.apply(element)
            }
        }
    }

    private data class ProcessingData(
        val fix: K2DiagnosticFix,
        val pointer: SmartPsiElementPointer<PsiElement>
    )
}

internal interface K2DiagnosticBasedProcessing<DIAGNOSTIC : KtDiagnosticWithPsi<*>> {
    val diagnosticClass: KClass<DIAGNOSTIC>

    context(KtAnalysisSession)
    fun createFix(diagnostic: DIAGNOSTIC) : K2DiagnosticFix?
}

internal class K2QuickFixDiagnosticBasedProcessing<DIAGNOSTIC : KtDiagnosticWithPsi<*>>(
    override val diagnosticClass: KClass<DIAGNOSTIC>,
    private val fixFactory: KotlinQuickFixFactory.IntentionBased<DIAGNOSTIC>
) : K2DiagnosticBasedProcessing<DIAGNOSTIC> {

    context(KtAnalysisSession)
    override fun createFix(diagnostic: DIAGNOSTIC): K2DiagnosticFix? {
        val quickfix = fixFactory.createQuickFixes(diagnostic).singleOrNull() ?: return null
        return object : K2DiagnosticFix {
            override fun apply(element: PsiElement) {
                quickfix.invoke(element.project, null, element.containingFile)
            }
        }
    }
}

internal class K2CustomDiagnosticBasedProcessing<DIAGNOSTIC : KtDiagnosticWithPsi<*>>(
    override val diagnosticClass: KClass<DIAGNOSTIC>,
    private val customFixFactory: (diagnostic: DIAGNOSTIC) -> K2DiagnosticFix?
) : K2DiagnosticBasedProcessing<DIAGNOSTIC> {

    context(KtAnalysisSession)
    override fun createFix(diagnostic: DIAGNOSTIC): K2DiagnosticFix? {
        return customFixFactory(diagnostic)
    }
}

internal interface K2DiagnosticFix {
    fun apply(element: PsiElement)
}
