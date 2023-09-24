// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.j2k.post.processing

import com.intellij.codeInsight.daemon.HighlightDisplayKey
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.profile.codeInspection.InspectionProfileManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiRecursiveElementVisitor
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractApplicabilityBasedInspection
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingRangeIntention
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.j2k.ConverterSettings
import org.jetbrains.kotlin.nj2k.NewJ2kConverterContext
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.utils.mapToIndex
import java.util.*


internal class InspectionLikeProcessingGroup(
    private val runSingleTime: Boolean = false,
    private val acceptNonKtElements: Boolean = false,
    private val processings: List<InspectionLikeProcessing>
) : FileBasedPostProcessing() {
    constructor(vararg processings: InspectionLikeProcessing) : this(
        runSingleTime = false,
        acceptNonKtElements = false,
        processings = processings.toList()
    )

    private val processingsToPriorityMap = processings.mapToIndex()
    fun priority(processing: InspectionLikeProcessing): Int = processingsToPriorityMap.getValue(processing)
    override fun runProcessing(file: KtFile, allFiles: List<KtFile>, rangeMarker: RangeMarker?, converterContext: NewJ2kConverterContext) {
        do {
            var modificationStamp: Long? = runReadAction { file.modificationStamp }
            val elementToActions = runReadAction {
                collectAvailableActions(file, converterContext, rangeMarker)
            }

            for ((processing, element, _) in elementToActions) {
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

    private enum class RangeFilterResult {
        SKIP,
        GO_INSIDE,
        PROCESS
    }

    private data class ProcessingData(
        val processing: InspectionLikeProcessing,
        val element: PsiElement,
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
                if (element is KtElement || acceptNonKtElements) {
                    val rangeResult = rangeFilter(element, rangeMarker)
                    if (rangeResult == RangeFilterResult.SKIP) return

                    super.visitElement(element)

                    if (rangeResult == RangeFilterResult.PROCESS) {
                        for (processing in processings) {
                            if (processing.isApplicableToElement(element, context.converter.settings)) {
                                availableActions.add(
                                    ProcessingData(processing, element, priority(processing))
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

internal abstract class InspectionLikeProcessing {
    abstract fun isApplicableToElement(element: PsiElement, settings: ConverterSettings?): Boolean
    abstract fun applyToElement(element: PsiElement)

    // Some post-processings may need to do some resolving operations when applying
    // Running it in outer write action may lead to UI freezes
    // So we let that post-processings to handle write actions by themselves
    open val writeActionNeeded = true

    val processingOptions: PostProcessingOptions = PostProcessingOptions.DEFAULT
}

internal abstract class InspectionLikeProcessingForElement<E : PsiElement>(private val classTag: Class<E>) : InspectionLikeProcessing() {
    protected abstract fun isApplicableTo(element: E, settings: ConverterSettings?): Boolean
    protected abstract fun apply(element: E)


    @Suppress("UNCHECKED_CAST")
    final override fun isApplicableToElement(element: PsiElement, settings: ConverterSettings?): Boolean {
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


internal inline fun <reified E : PsiElement, I : SelfTargetingRangeIntention<E>> intentionBasedProcessing(
    intention: I,
    writeActionNeeded: Boolean = true,
    noinline additionalChecker: (E) -> Boolean = { true }
) = object : InspectionLikeProcessingForElement<E>(E::class.java) {
    override fun isApplicableTo(element: E, settings: ConverterSettings?): Boolean =
        intention.applicabilityRange(element) != null
                && additionalChecker(element)

    override fun apply(element: E) {
        intention.applyTo(element, null)
    }

    override val writeActionNeeded = writeActionNeeded
}

internal inline fun <reified E : PsiElement, I : AbstractApplicabilityBasedInspection<E>> inspectionBasedProcessing(
    inspection: I,
    writeActionNeeded: Boolean = true,
    noinline additionalChecker: (E) -> Boolean = { true }
) = object : InspectionLikeProcessingForElement<E>(E::class.java) {
    override fun isApplicableTo(element: E, settings: ConverterSettings?): Boolean =
        isInspectionEnabledInCurrentProfile(inspection, element.project) && inspection.isApplicable(element) && additionalChecker(element)

    override fun apply(element: E) {
        inspection.applyTo(element)
    }

    override val writeActionNeeded = writeActionNeeded
}

internal fun isInspectionEnabledInCurrentProfile(inspection: AbstractKotlinInspection, project: Project): Boolean {
    if (isUnitTestMode()) {
        // there is no real inspection profile in J2K tests, consider all inspections to be enabled in tests
        return true
    }
    val inspectionProfile = InspectionProfileManager.getInstance(project).getCurrentProfile()
    val highlightDisplayKey = HighlightDisplayKey.findById(inspection.getID())
    return inspectionProfile.isToolEnabled(highlightDisplayKey)
}