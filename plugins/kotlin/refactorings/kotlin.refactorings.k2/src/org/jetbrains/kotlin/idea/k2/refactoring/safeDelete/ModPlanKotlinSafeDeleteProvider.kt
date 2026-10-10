// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.safeDelete

import com.intellij.model.SymbolPointer
import com.intellij.psi.PsiElement
import com.intellij.refactoring.modplan.ModPlanAnalysisResult
import com.intellij.refactoring.modplan.ModPlanChain
import com.intellij.refactoring.modplan.ModPlanFinalStep
import com.intellij.refactoring.modplan.ModPlanChainBuilder
import com.intellij.refactoring.modplan.ModPlanEdit
import com.intellij.refactoring.modplan.ModPlanOptionValues
import com.intellij.refactoring.modplan.ModPlanQuestion
import com.intellij.refactoring.modplan.ModPlanRequest
import com.intellij.refactoring.modplan.safeDelete.ModPlanLegacySafeDeleteProvider
import com.intellij.refactoring.modplan.safeDelete.ModPlanSafeDeleteOptions
import com.intellij.refactoring.modplan.safeDelete.ModPlanSafeDeleteProvider
import com.intellij.refactoring.modplan.safeDelete.ModPlanSafeDeleteProvider.DeletionDecision
import com.intellij.refactoring.modplan.safeDelete.ModPlanSafeDeleteUsageInfoUtil
import com.intellij.usageView.UsageInfo
import com.intellij.util.containers.MultiMap
import java.util.Collections

/**
 * The safe delete of Kotlin declarations on the ModPlan engine: it runs the code of [KotlinFirSafeDeleteProcessor], and its options answer
 * the questions that the interactive safe delete asks in dialogs. It owns an element if [KotlinFirSafeDeleteProcessor] is the first legacy
 * delegate that handles it.
 *
 * The processor has no additional elements to delete, no question about the usages and nothing to filter in its usages, so none of them is
 * here.
 *
 * **Known differences from the legacy flow:**
 * - the usages are not filtered, as the legacy processor does not either, but the conflicts are found per element;
 * - the preparation of an element runs among the edits, not after all of them;
 * - the events list every deleted element, usages included;
 * - the search options of several elements are kept by the first element, where the legacy dialog uses the common settings; a file asks
 *   nothing about them, where the legacy dialog asks for its classes;
 * - the expected declaration of several actual ones is asked about once.
 */
internal class ModPlanKotlinSafeDeleteProvider : ModPlanSafeDeleteProvider {
    override fun isApplicable(subject: SymbolPointer): Boolean {
        val element = subject.element() ?: return false
        return ModPlanLegacySafeDeleteProvider.legacyHandlerOf(element) is KotlinFirSafeDeleteProcessor
    }

    /**
     * For a requested subject it runs the first legacy steps, as the interactive safe delete does before its dialog: the chain of
     * `getElementsToSearch`, and the search options, and adds what is deleted with the subject. With
     * [ModPlanSafeDeleteOptions.SEARCH_RELATED] false it asks only the search options. A subject with nothing to search is refused. If the
     * user chose to delete the base declarations instead, they are deleted instead of the subject, and the subject stays, as in the legacy
     * safe delete.
     *
     * The questions go in this order: the ones of `getElementsToSearch`, such as the base declarations to delete, then the search options.
     */
    override fun prepareRequest(
        chainBuilder: ModPlanChainBuilder,
        subject: SymbolPointer,
        deletedSubjects: List<SymbolPointer>,
    ): ModPlanChain<DeletionDecision> {
        val target = subject.element() ?: return chainBuilder.passValue(DeletionDecision.REFUSED)
        if (subject !in chainBuilder.request.subjects()) {
            return chainBuilder.passValue(DeletionDecision.DELETED)
        }
        val searchQuestion = ModPlanSafeDeleteUsageInfoUtil.searchQuestion(target, deletedSubjects, KotlinSearchStorage(subject))
        if (!chainBuilder.request.optionValues().get(ModPlanSafeDeleteOptions.SEARCH_RELATED)) {
            return withSearchOptions(chainBuilder, listOf(target), searchQuestion, replaced = false)
        }
        val deletedWithTarget = ModPlanSafeDeleteUsageInfoUtil.deletedWith(target, deletedSubjects)
        return kotlinElementsToSearch(chainBuilder, target, deletedWithTarget).andThen { afterElements, elements ->
            // The question about the base declarations came before, so the target is read again.
            val currentTarget = subject.element()
            withSearchOptions(afterElements, elements, searchQuestion, replaced = currentTarget != null && currentTarget !in elements)
        }
    }

    /**
     * Goes on with the elements to search: asks the search options, then ends with the preparation that deletes the elements with the
     * subject, or instead of it. With no elements, the subject is refused.
     *
     * @param replaced whether the elements are deleted instead of the subject, such as the base declarations that the user chose
     */
    private fun withSearchOptions(
        chainBuilder: ModPlanChainBuilder,
        elements: List<PsiElement>,
        searchQuestion: ModPlanQuestion?,
        replaced: Boolean,
    ): ModPlanChain<DeletionDecision> {
        if (elements.isEmpty()) return chainBuilder.passValue(DeletionDecision.REFUSED)
        val elementSubjects = elements.map { SymbolPointer.of(it) }
        val preparation = if (replaced) {
            DeletionDecision.replacedBy(elementSubjects)
        }
        else {
            DeletionDecision.deletedWith(elementSubjects)
        }
        return chainBuilder.ask(searchQuestion) { it.passValue(preparation) }
    }

    override fun analyze(
        chainBuilder: ModPlanChainBuilder,
        subject: SymbolPointer,
        deletedSubjects: List<SymbolPointer>,
    ): ModPlanChain<ModPlanFinalStep<ModPlanAnalysisResult>> {
        // The analysis asks nothing, so the search and the edits run in the step at the end of the run, after every question.
        return chainBuilder.finish { request -> analysisOf(subject, deletedSubjects, request) }
    }

    private fun analysisOf(subject: SymbolPointer, deletedSubjects: List<SymbolPointer>, request: ModPlanRequest): ModPlanAnalysisResult {
        val target = subject.element() ?: return ModPlanAnalysisResult.Refused(ModPlanAnalysisResult.RefusedReason.NOT_APPLICABLE)
        val usages = search(target, deletedSubjects, request)
        val legacyMessages = MultiMap<PsiElement, String>().also { collectKotlinConflicts(target, it, headless = true) }
        val deleteExpectActual = request.optionValues().get(ModPlanKotlinSafeDeleteOptions.DELETE_EXPECT_ACTUAL)
        return ModPlanSafeDeleteUsageInfoUtil.analyzeUsages(
            target, usages, usages, legacyMessages,
            ModPlanEdit.update(target) { copy -> prepareKotlinForDeletion(copy, deleteExpectActual) }
        )
    }

    /** Keeps the search options in the settings that [KotlinFirSafeDeleteProcessor] uses for the kind of the element. */
    private class KotlinSearchStorage(private val subject: SymbolPointer) : ModPlanQuestion.Storage {
        override fun load(optionValues: ModPlanOptionValues): ModPlanOptionValues {
            val element = subject.element() ?: return optionValues
            return optionValues.with(ModPlanSafeDeleteOptions.SEARCH_IN_COMMENTS, searchesInComments(element))
                .with(ModPlanSafeDeleteOptions.SEARCH_IN_NON_CODE, searchesForText(element))
        }

        override fun store(answers: ModPlanOptionValues) {
            val element = subject.element() ?: return
            setSearchesInComments(element, answers.get(ModPlanSafeDeleteOptions.SEARCH_IN_COMMENTS))
            setSearchesForText(element, answers.get(ModPlanSafeDeleteOptions.SEARCH_IN_NON_CODE))
        }
    }
}

/** @return the usages of [target] in the deletion of [deletedSubjects]: `findUsages` and the non-code search */
private fun search(target: PsiElement, deletedSubjects: List<SymbolPointer>, request: ModPlanRequest): List<UsageInfo> {
    val rawUsages = Collections.synchronizedList(ArrayList<UsageInfo>())
    val info = findKotlinUsages(target, ModPlanSafeDeleteUsageInfoUtil.elementsOf(deletedSubjects), rawUsages)
    return ModPlanSafeDeleteUsageInfoUtil.withNonCodeUsages(info, rawUsages, request.project(), request.optionValues())
}
