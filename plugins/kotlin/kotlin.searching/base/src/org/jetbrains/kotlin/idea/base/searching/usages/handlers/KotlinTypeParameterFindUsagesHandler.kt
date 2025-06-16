// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.base.searching.usages.handlers

import com.intellij.find.findUsages.AbstractFindUsagesDialog
import com.intellij.find.findUsages.FindUsagesOptions
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiElement
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.usageView.UsageInfo
import com.intellij.util.Processor
import org.jetbrains.kotlin.idea.base.searching.usages.KotlinFindUsagesHandlerFactory
import org.jetbrains.kotlin.idea.base.searching.usages.dialogs.KotlinTypeParameterFindUsagesDialog
import org.jetbrains.kotlin.idea.base.util.useScope
import org.jetbrains.kotlin.psi.KtTypeParameter

class KotlinTypeParameterFindUsagesHandler(
    element: KtTypeParameter,
    factory: KotlinFindUsagesHandlerFactory
) : KotlinFindUsagesHandler<KtTypeParameter>(element, factory) {
    override fun getFindUsagesDialog(
        isSingleFile: Boolean, toShowInNewTab: Boolean, mustOpenInNewTab: Boolean
    ): AbstractFindUsagesDialog = KotlinTypeParameterFindUsagesDialog(
        getElement(), project, findUsagesOptions, toShowInNewTab, mustOpenInNewTab, isSingleFile, this
    )

    override fun createSearcher(
        element: PsiElement,
        processor: Processor<in UsageInfo>,
        options: FindUsagesOptions
    ): Searcher = object : Searcher(element, processor, options) {
        override fun buildTaskList(forHighlight: Boolean): Boolean {
            addTask {
                runReadAction {
                    val searchScope = element.useScope().intersectWith(options.searchScope)
                    ReferencesSearch.search(element, searchScope).asIterable().all { processUsage(processor, it) }
                }
            }

            return true
        }
    }

    override fun getFindUsagesOptions(dataContext: DataContext?): FindUsagesOptions {
        val usagesOptions = factory.defaultOptions.clone()
        usagesOptions.isSearchForTextOccurrences = false
        return usagesOptions
    }
}
