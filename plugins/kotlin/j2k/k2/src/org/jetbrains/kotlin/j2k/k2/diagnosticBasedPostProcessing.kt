// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.j2k.k2

import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.editor.asTextRange
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.createSmartPointer
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.KaDiagnosticCheckerFilter.ONLY_COMMON_CHECKERS
import org.jetbrains.kotlin.analysis.api.diagnostics.KaDiagnosticWithPsi
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.quickfix.AddExclExclCallFix
import org.jetbrains.kotlin.j2k.FileBasedPostProcessing
import org.jetbrains.kotlin.j2k.PostProcessingApplier
import org.jetbrains.kotlin.nj2k.NewJ2kConverterContext
import org.jetbrains.kotlin.psi.KtFile
import kotlin.reflect.KClass

internal class K2DiagnosticBasedPostProcessingGroup(
    vararg diagnosticBasedProcessings: K2DiagnosticBasedProcessing<KaDiagnosticWithPsi<*>>
) : FileBasedPostProcessing() {

    private val diagnosticToProcessing: Map<KClass<out KaDiagnosticWithPsi<*>>, K2DiagnosticBasedProcessing<KaDiagnosticWithPsi<*>>> =
        diagnosticBasedProcessings.associateBy({ it.diagnosticClass }, { it })

    override fun runProcessing(file: KtFile, allFiles: List<KtFile>, rangeMarker: RangeMarker?, converterContext: NewJ2kConverterContext) {
        error("Not supported in K2 J2K")
    }

    context(KaSession)
    override fun computeApplier(
        file: KtFile,
        allFiles: List<KtFile>,
        rangeMarker: RangeMarker?,
        converterContext: NewJ2kConverterContext
    ): PostProcessingApplier {
        // TODO: for copy-paste conversion, try to restrict the analysis range, like in K1 J2K
        //  (see org.jetbrains.kotlin.idea.j2k.post.processing.DiagnosticBasedPostProcessingGroup.analyzeFileRange)
        val diagnostics = file.collectDiagnostics(filter = ONLY_COMMON_CHECKERS)
        val processingDataList = mutableListOf<ProcessingData>()

        for (diagnostic in diagnostics) {
            val processingData = processDiagnostic(diagnostic, file, rangeMarker)
            if (processingData != null) {
                processingDataList += processingData
            }
        }

        return Applier(processingDataList, file.project)
    }

    context(KaSession)
    private fun processDiagnostic(diagnostic: KaDiagnosticWithPsi<*>, file: KtFile, rangeMarker: RangeMarker?): ProcessingData? {
        val processing = diagnosticToProcessing[diagnostic.diagnosticClass] ?: return null
        val element = diagnostic.psi
        val range = rangeMarker?.asTextRange ?: file.textRange

        if (!range.contains(element.textRange)) return null

        val fix = processing.createFix(diagnostic) ?: return null
        return ProcessingData(fix, element.createSmartPointer())
    }

    private class Applier(private val processingDataList: List<ProcessingData>, private val project: Project) : PostProcessingApplier {
        override fun apply() {
            CodeStyleManager.getInstance(project).performActionWithFormatterDisabled {
                for ((fix, pointer) in processingDataList) {
                    val element = pointer.element ?: continue
                    fix.apply(element)
                }
            }
        }
    }

    private data class ProcessingData(
        val fix: K2DiagnosticFix,
        val pointer: SmartPsiElementPointer<PsiElement>
    )
}

internal interface K2DiagnosticBasedProcessing<DIAGNOSTIC : KaDiagnosticWithPsi<*>> {
    val diagnosticClass: KClass<DIAGNOSTIC>

    context(KaSession)
    fun createFix(diagnostic: DIAGNOSTIC): K2DiagnosticFix?
}

@Suppress("unused") // TODO will probably be used later for diagnostics that produce a single quickfix
internal class K2QuickFixDiagnosticBasedProcessing<DIAGNOSTIC : KaDiagnosticWithPsi<*>>(
    override val diagnosticClass: KClass<DIAGNOSTIC>,
    private val fixFactory: KotlinQuickFixFactory.IntentionBased<DIAGNOSTIC>
) : K2DiagnosticBasedProcessing<DIAGNOSTIC> {

    context(KaSession)
    override fun createFix(diagnostic: DIAGNOSTIC): K2DiagnosticFix? {
        val quickfix = fixFactory.createQuickFixes(diagnostic).singleOrNull() ?: return null
        return object : K2DiagnosticFix {
            override fun apply(element: PsiElement) {
                quickfix.invoke(element.project, null, element.containingFile)
            }
        }
    }
}

internal class K2AddExclExclDiagnosticBasedProcessing<DIAGNOSTIC : KaDiagnosticWithPsi<*>>(
    override val diagnosticClass: KClass<DIAGNOSTIC>,
    private val fixFactory: KotlinQuickFixFactory.IntentionBased<DIAGNOSTIC>
) : K2DiagnosticBasedProcessing<DIAGNOSTIC> {

    context(KaSession)
    override fun createFix(diagnostic: DIAGNOSTIC): K2DiagnosticFix? {
        val addExclExclCallFix = fixFactory.createQuickFixes(diagnostic).firstOrNull { it is AddExclExclCallFix } ?: return null
        return object : K2DiagnosticFix {
            override fun apply(element: PsiElement) {
                addExclExclCallFix.invoke(element.project, null, element.containingFile)
            }
        }
    }
}

internal class K2CustomDiagnosticBasedProcessing<DIAGNOSTIC : KaDiagnosticWithPsi<*>>(
    override val diagnosticClass: KClass<DIAGNOSTIC>,
    private val customFixFactory: (diagnostic: DIAGNOSTIC) -> K2DiagnosticFix?
) : K2DiagnosticBasedProcessing<DIAGNOSTIC> {

    context(KaSession)
    override fun createFix(diagnostic: DIAGNOSTIC): K2DiagnosticFix? {
        return customFixFactory(diagnostic)
    }
}

internal interface K2DiagnosticFix {
    fun apply(element: PsiElement)
}
