// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.j2k.post.processing

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.RangeMarker
import com.intellij.psi.PsiElement
import com.intellij.refactoring.suggested.range
import org.jetbrains.kotlin.caches.resolve.KotlinCacheService
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.DiagnosticFactory
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.QuickFixFactory
import org.jetbrains.kotlin.idea.quickfix.ChangeVisibilityFix.*
import org.jetbrains.kotlin.idea.quickfix.asKotlinIntentionActionsFactory
import org.jetbrains.kotlin.idea.resolve.ResolutionFacade
import org.jetbrains.kotlin.nj2k.NewJ2kConverterContext
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.psiUtil.elementsInRange
import org.jetbrains.kotlin.resolve.diagnostics.Diagnostics
import kotlin.reflect.KFunction1

internal class DiagnosticBasedPostProcessingGroup(diagnosticBasedProcessings: List<DiagnosticBasedProcessing>) : FileBasedPostProcessing() {
    constructor(vararg diagnosticBasedProcessings: DiagnosticBasedProcessing) : this(diagnosticBasedProcessings.toList())

    private val diagnosticToFixes: Map<DiagnosticFactory<*>, List<KFunction1<Diagnostic, Unit>>> =
        diagnosticBasedProcessings.asSequence().flatMap { processing ->
            processing.diagnosticFactories.asSequence().map { it to processing::fix }
        }.groupBy { it.first }.mapValues { (_, list) ->
            list.map { it.second }
        }

    override fun runProcessing(file: KtFile, allFiles: List<KtFile>, rangeMarker: RangeMarker?, converterContext: NewJ2kConverterContext) {
        val diagnostics = runReadAction {
            val resolutionFacade = KotlinCacheService.getInstance(converterContext.project).getResolutionFacade(allFiles)
            analyzeFileRange(file, rangeMarker, resolutionFacade).all()
        }

        for (diagnostic in diagnostics) {
            processDiagnostic(diagnostic, file, rangeMarker)
        }
    }

    private fun analyzeFileRange(file: KtFile, rangeMarker: RangeMarker?, resolutionFacade: ResolutionFacade): Diagnostics {
        val elements = when {
            rangeMarker == null -> listOf(file)
            rangeMarker.isValid -> file.elementsInRange(rangeMarker.range!!).filterIsInstance<KtElement>()
            else -> emptyList()
        }

        return if (elements.isNotEmpty())
            resolutionFacade.analyzeWithAllCompilerChecks(elements).bindingContext.diagnostics
        else Diagnostics.EMPTY
    }

    private fun processDiagnostic(diagnostic: Diagnostic, file: KtFile, rangeMarker: RangeMarker?) {
        val fixes = diagnosticToFixes[diagnostic.factory] ?: return
        val elementIsInRange = runReadAction {
            val range = rangeMarker?.range ?: file.textRange
            range.contains(diagnostic.psiElement.textRange)
        }
        if (!elementIsInRange) return

        for (fix in fixes) {
            val elementIsValid = runReadAction { diagnostic.psiElement.isValid }
            if (elementIsValid) {
                fix(diagnostic)
            }
        }
    }
}

internal interface DiagnosticBasedProcessing {
    val diagnosticFactories: List<DiagnosticFactory<*>>
    fun fix(diagnostic: Diagnostic)
}

internal inline fun <reified T : PsiElement> diagnosticBasedProcessing(
    vararg diagnosticFactory: DiagnosticFactory<*>,
    crossinline fix: (T, Diagnostic) -> Unit
) =
    object : DiagnosticBasedProcessing {
        override val diagnosticFactories = diagnosticFactory.toList()
        override fun fix(diagnostic: Diagnostic) {
            val element = diagnostic.psiElement as? T
            if (element != null) runUndoTransparentActionInEdt(inWriteAction = true) {
                fix(element, diagnostic)
            }
        }
    }

internal fun diagnosticBasedProcessing(fixFactory: QuickFixFactory, vararg diagnosticFactory: DiagnosticFactory<*>) =
    object : DiagnosticBasedProcessing {
        override val diagnosticFactories = diagnosticFactory.toList()
        override fun fix(diagnostic: Diagnostic) {
            val actionFactory = fixFactory.asKotlinIntentionActionsFactory()
            val fix = runReadAction { actionFactory.createActions(diagnostic).singleOrNull() } ?: return
            runUndoTransparentActionInEdt(inWriteAction = true) {
                fix.invoke(diagnostic.psiElement.project, null, diagnostic.psiFile)
            }
        }
    }

internal fun invisibleMemberDiagnosticBasedProcessing(fixFactory: QuickFixFactory, vararg diagnosticFactory: DiagnosticFactory<*>) =
    object : DiagnosticBasedProcessing {
        override val diagnosticFactories = diagnosticFactory.toList()

        override fun fix(diagnostic: Diagnostic) {
            val actionFactory = fixFactory.asKotlinIntentionActionsFactory()
            val fixes = runReadAction { actionFactory.createActions(diagnostic) }
            val leastVisibilityFix = fixes.find { it is ChangeToProtectedFix }
                ?: fixes.find { it is ChangeToInternalFix }
                ?: fixes.find { it is ChangeToPublicFix }
                ?: return

            runUndoTransparentActionInEdt(inWriteAction = true) {
                leastVisibilityFix.invoke(diagnostic.psiElement.project, null, diagnostic.psiFile)
            }
        }
    }

internal fun exposedVisibilityDiagnosticBasedProcessing(fixFactory: QuickFixFactory, vararg diagnosticFactory: DiagnosticFactory<*>) =
    object : DiagnosticBasedProcessing {
        override val diagnosticFactories = diagnosticFactory.toList()

        override fun fix(diagnostic: Diagnostic) {
            val actionFactory = fixFactory.asKotlinIntentionActionsFactory()
            val changeToPublicFix = runReadAction { actionFactory.createActions(diagnostic).find { it is ChangeToPublicFix } } ?: return
            runUndoTransparentActionInEdt(inWriteAction = true) {
                changeToPublicFix.invoke(diagnostic.psiElement.project, null, diagnostic.psiFile)
            }
        }
    }
