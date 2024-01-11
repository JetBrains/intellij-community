// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.j2k

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiRecursiveElementVisitor
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.refactoring.suggested.range
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.kotlin.idea.caches.resolve.getResolutionFacade
import org.jetbrains.kotlin.idea.caches.resolve.resolveImportReference
import org.jetbrains.kotlin.idea.core.KotlinPluginDisposable
import org.jetbrains.kotlin.idea.core.util.EDT
import org.jetbrains.kotlin.idea.util.ImportInsertHelper
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.psiUtil.elementsInRange
import org.jetbrains.kotlin.resolve.diagnostics.Diagnostics

class J2kPostProcessor(private val formatCode: Boolean) : PostProcessor {

    override val phasesCount: Int = 1

    override fun insertImport(file: KtFile, fqName: FqName) {
        ApplicationManager.getApplication().invokeAndWait {
            runWriteAction {
                val descriptors = file.resolveImportReference(fqName)
                descriptors.firstOrNull()?.let { ImportInsertHelper.getInstance(file.project).importDescriptor(file, it) }
            }
        }
    }

    private enum class RangeFilterResult {
        SKIP,
        GO_INSIDE,
        PROCESS
    }

    override fun doAdditionalProcessing(
        target: JKPostProcessingTarget,
        converterContext: ConverterContext?,
        onPhaseChanged: ((Int, String) -> Unit)?
    ) {
        val (file, rangeMarker) = when (target) {
            is JKPieceOfCodePostProcessingTarget -> target.file to target.rangeMarker
            is JKMultipleFilesPostProcessingTarget -> target.files.single() to null
        }

        val disposable = KotlinPluginDisposable.getInstance(file.project)

        runBlocking(EDT.ModalityStateElement(ModalityState.defaultModalityState())) {
            do {
                var modificationStamp: Long? = file.modificationStamp
                val elementToActions: List<ActionData> = run {
                    @Suppress("DEPRECATION")
                    while (!Disposer.isDisposed(disposable)) {
                        try {
                            return@run runReadAction {
                                collectAvailableActions(file, rangeMarker)
                            }
                        } catch (e: Exception) {
                            if (e is ControlFlowException) continue
                            throw e
                        }
                    }
                    emptyList()
                }

                withContext(EDT) {
                    for ((element, action, _, writeActionNeeded) in elementToActions) {
                        if (element.isValid) {
                            if (writeActionNeeded) {
                                runWriteAction {
                                    action()
                                }
                            } else {
                                action()
                            }
                        } else {
                            modificationStamp = null
                        }
                    }
                }

                if (modificationStamp == file.modificationStamp) break
            } while (elementToActions.isNotEmpty())


            if (formatCode) {
                withContext(EDT) {
                    runWriteAction {
                        val codeStyleManager = CodeStyleManager.getInstance(file.project)
                        if (rangeMarker != null) {
                            if (rangeMarker.isValid) {
                                codeStyleManager.reformatRange(file, rangeMarker.startOffset, rangeMarker.endOffset)
                            }
                        } else {
                            codeStyleManager.reformat(file)
                        }
                        Unit
                    }
                }
            }
        }
    }

    private data class ActionData(val element: KtElement, val action: () -> Unit, val priority: Int, val writeActionNeeded: Boolean)

    private fun collectAvailableActions(file: KtFile, rangeMarker: RangeMarker?): List<ActionData> {
        val diagnostics = analyzeFileRange(file, rangeMarker)

        val availableActions = ArrayList<ActionData>()

        file.accept(object : PsiRecursiveElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (element is KtElement) {
                    val rangeResult = rangeFilter(element, rangeMarker)
                    if (rangeResult == RangeFilterResult.SKIP) return

                    super.visitElement(element)

                    if (rangeResult == RangeFilterResult.PROCESS) {
                        val postProcessingRegistrar = J2KPostProcessingRegistrar.instance
                        postProcessingRegistrar.processings.forEach { processing ->
                            val action = processing.createAction(element, diagnostics)
                            if (action != null) {
                                availableActions.add(
                                    ActionData(
                                        element, action,
                                        postProcessingRegistrar.priority(processing),
                                        processing.writeActionNeeded
                                    )
                                )
                            }
                        }
                    }
                }
            }
        })
        availableActions.sortBy { it.priority }
        return availableActions
    }

    private fun analyzeFileRange(file: KtFile, rangeMarker: RangeMarker?): Diagnostics {
        val range = rangeMarker?.range
        val elements = if (range == null)
            listOf(file)
        else
            file.elementsInRange(range).filterIsInstance<KtElement>()

        return if (elements.isNotEmpty())
            file.getResolutionFacade().analyzeWithAllCompilerChecks(elements).bindingContext.diagnostics
        else
            Diagnostics.EMPTY
    }

    private fun rangeFilter(element: PsiElement, rangeMarker: RangeMarker?): RangeFilterResult {
        if (rangeMarker == null) return RangeFilterResult.PROCESS
        if (!rangeMarker.isValid) return RangeFilterResult.SKIP
        val range = TextRange(rangeMarker.startOffset, rangeMarker.endOffset)
        val elementRange = element.textRange
        return when {
            range.contains(elementRange) -> RangeFilterResult.PROCESS
            range.intersects(elementRange) -> RangeFilterResult.GO_INSIDE
            else -> RangeFilterResult.SKIP
        }
    }
}
