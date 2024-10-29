// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.j2k

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiRecursiveElementVisitor
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.createSmartPointer
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.j2k.InspectionLikeProcessingGroup.RangeFilterResult.*
import org.jetbrains.kotlin.nj2k.NewJ2kConverterContext
import org.jetbrains.kotlin.nj2k.runUndoTransparentActionInEdt
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.utils.mapToIndex

class InspectionLikeProcessingGroup(
    private val runSingleTime: Boolean = false,
    private val processings: List<InspectionLikeProcessing>
) : FileBasedPostProcessing() {
    constructor(vararg processings: InspectionLikeProcessing) : this(runSingleTime = false, processings.toList())

    private val processingsToPriorityMap: Map<InspectionLikeProcessing, Int> =
        processings.mapToIndex()

    fun priority(processing: InspectionLikeProcessing): Int =
        processingsToPriorityMap.getValue(processing)

    override fun runProcessing(file: KtFile, allFiles: List<KtFile>, rangeMarker: RangeMarker?, converterContext: NewJ2kConverterContext) {
        do {
            var modificationStamp: Long? = runReadAction { file.modificationStamp }
            val elementToActions = runReadAction {
                collectAvailableActions(file, converterContext, rangeMarker)
            }

            for ((processing, pointer, _) in elementToActions) {
                val element = runReadAction { pointer.element }
                if (element == null) {
                    modificationStamp = null
                    continue
                }

                val needRun = runReadAction {
                    element.isValid && processing.isApplicableToElement(element, converterContext.converter.settings)
                }

                if (needRun) runUndoTransparentActionInEdt(inWriteAction = processing.writeActionNeeded) {
                    processing.applyToElement(element)
                } else {
                    modificationStamp = null
                }
            }

            if (runSingleTime) break
        } while (modificationStamp != runReadAction { file.modificationStamp } && elementToActions.isNotEmpty())
    }

    context(KaSession)
    override fun computeApplier(
        file: KtFile,
        allFiles: List<KtFile>,
        rangeMarker: RangeMarker?,
        converterContext: NewJ2kConverterContext
    ): PostProcessingApplier {
        val processingDataList = collectAvailableActions(file, converterContext, rangeMarker)
        return Applier(processingDataList, file.project)
    }

    private class Applier(private val processingDataList: List<ProcessingData>, private val project: Project) : PostProcessingApplier {
        override fun apply() {
            CodeStyleManager.getInstance(project).performActionWithFormatterDisabled {
                for ((processing, pointer, _) in processingDataList) {
                    val element = pointer.element ?: continue
                    processing.applyToElement(element)
                }
            }
        }
    }

    private enum class RangeFilterResult { SKIP, GO_INSIDE, PROCESS }

    private data class ProcessingData(
        val processing: InspectionLikeProcessing,
        val pointer: SmartPsiElementPointer<PsiElement>,
        val priority: Int
    )

    private fun collectAvailableActions(
        file: KtFile,
        context: NewJ2kConverterContext,
        rangeMarker: RangeMarker?
    ): List<ProcessingData> {
        val availableActions = ArrayList<ProcessingData>()

        file.accept(object : PsiRecursiveElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (element !is KtElement) return
                val rangeResult = rangeFilter(element, rangeMarker)
                if (rangeResult == SKIP) return

                super.visitElement(element)

                if (rangeResult == PROCESS) {
                    for (processing in processings) {
                        if (processing.isApplicableToElement(element, context.converter.settings)) {
                            availableActions.add(ProcessingData(processing, element.createSmartPointer(), priority(processing)))
                        }
                    }
                }
            }
        })

        availableActions.sortBy { it.priority }
        return availableActions
    }

    @Suppress("DuplicatedCode") // copied from Old J2K
    private fun rangeFilter(element: PsiElement, rangeMarker: RangeMarker?): RangeFilterResult {
        if (rangeMarker == null) return PROCESS
        if (!rangeMarker.isValid) return SKIP
        val range = TextRange(rangeMarker.startOffset, rangeMarker.endOffset)
        val elementRange = element.textRange
        return when {
            range.contains(elementRange) -> PROCESS
            range.intersects(elementRange) -> GO_INSIDE
            else -> SKIP
        }
    }
}

abstract class InspectionLikeProcessing {
    abstract fun isApplicableToElement(element: PsiElement, settings: ConverterSettings): Boolean

    abstract fun applyToElement(element: PsiElement)

    // Some post-processings may need to do some resolving operations when applying them.
    // Running it in outer write action may lead to UI freezes,
    // so we let those post-processings handle write actions by themselves.
    open val writeActionNeeded = true

    val processingOptions: PostProcessingOptions = PostProcessingOptions.DEFAULT
}

abstract class InspectionLikeProcessingForElement<E : PsiElement>(private val classTag: Class<E>) : InspectionLikeProcessing() {
    protected abstract fun isApplicableTo(element: E, settings: ConverterSettings): Boolean

    protected abstract fun apply(element: E)

    @Suppress("UNCHECKED_CAST")
    final override fun isApplicableToElement(element: PsiElement, settings: ConverterSettings): Boolean {
        if (!classTag.isInstance(element)) return false
        if (!element.isValid) return false
        @Suppress("UNCHECKED_CAST") return isApplicableTo(element as E, settings)
    }

    final override fun applyToElement(element: PsiElement) {
        if (!classTag.isInstance(element)) return
        if (!element.isValid) return
        @Suppress("UNCHECKED_CAST") apply(element as E)
    }
}