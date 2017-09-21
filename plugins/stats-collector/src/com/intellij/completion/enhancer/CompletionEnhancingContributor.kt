/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.completion.enhancer

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.completion.CompletionResult.*
import com.intellij.codeInsight.lookup.*
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.ide.plugins.PluginManager
import com.intellij.lang.Language
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PreloadingActivity
import com.intellij.openapi.extensions.ExtensionPoint
import com.intellij.openapi.extensions.Extensions
import com.intellij.openapi.extensions.LoadingOrder
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.extensions.impl.ExtensionComponentAdapter
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.Key
import com.intellij.sorting.language
import com.intellij.stats.completion.prefixLength
import com.intellij.util.ReflectionUtil
import org.picocontainer.ComponentAdapter


object CompletionContributors {

    fun add(contributorEP: CompletionContributorEP) {
        val extensionPoint = extensionPoint()
        extensionPoint.registerExtension(contributorEP)
    }

    fun remove(contributorEP: CompletionContributorEP) {
        val extensionPoint = extensionPoint()
        extensionPoint.unregisterExtension(contributorEP)
    }

    fun first(): CompletionContributorEP {
        val extensionPoint = extensionPoint()
        return extensionPoint.extensions.first()
    }

    fun addFirst(contributorEP: CompletionContributorEP) {
        val extensionPoint = extensionPoint()
        val first = extensionPoint.extensions.first() as CompletionContributorEP
        val id = contributorOrderId(first)
        val order = LoadingOrder.readOrder("first, before $id")
        extensionPoint.registerExtension(contributorEP, order)
    }

    private fun extensionPoint(): ExtensionPoint<CompletionContributorEP> {
        return Extensions.getRootArea().getExtensionPoint<CompletionContributorEP>("com.intellij.completion.contributor")
    }

    fun removeFirst() {
        val point = extensionPoint()
        val first = point.extensions.first()
        point.unregisterExtension(first)
    }

    private fun contributorOrderId(contributorEP: CompletionContributorEP): String? {
        val className = contributorEP.implementationClass
        val picoContainer = Extensions.getRootArea().picoContainer
        val adapterForFirstContributor = (picoContainer.componentAdapters as Collection<ComponentAdapter>)
                .asSequence()
                .filter {
                    it is ExtensionComponentAdapter
                            && ReflectionUtil.isAssignable(CompletionContributorEP::class.java, it.componentImplementation)
                }
                .map { it to it.getComponentInstance(picoContainer) as CompletionContributorEP }
                .find { it.second.implementationClass == className }?.first as? ExtensionComponentAdapter

        return adapterForFirstContributor?.orderId
    }

}



class FirstContributorPreloader : PreloadingActivity() {

    override fun preload(indicator: ProgressIndicator) {
        val id = PluginId.findId("com.intellij.stats.completion")
        val descriptor = PluginManager.getPlugin(id)

        CompletionContributorEP().apply {
            implementationClass = InvocationCountEnhancingContributor::class.java.name
            language = "any"
            pluginDescriptor = descriptor
        }.let {
            CompletionContributors.addFirst(it)
        }
    }

}


/**
 * Runs all remaining contributors and then starts another completion round with max invocation count,
 * All lookup elements added would be sorted with another sorter and will appear at the bottom of completion lookup
 */
class InvocationCountEnhancingContributor : CompletionContributor() {
    companion object {
        private val MAX_INVOCATION_COUNT = 5

        var RUN_COMPLETION_AFTER_CHARS = 2
        var isEnabledInTests = false
    }

    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
        if (ApplicationManager.getApplication().isUnitTestMode && !isEnabledInTests) return

        val addedElements = HashSet<LookupElement>()
        val newSorter = sorter(parameters, result.prefixMatcher).weigh(CompletionNumberWeigher())

        val start = System.currentTimeMillis()
        result.runRemainingContributors(parameters, {
            InvocationCountOrigin.setInvocationTime(it.lookupElement, parameters.invocationCount)
            addedElements.add(it.lookupElement)
            wrap(it.lookupElement, it.prefixMatcher, newSorter)?.let { result.passResult(it) }
        })
        val end = System.currentTimeMillis()

        parameters.language()?.registerCompletionContributorsTime(end - start)

        val lookup = LookupManager.getActiveLookup(parameters.editor) as LookupImpl? ?: return
        val typedChars = lookup.prefixLength()
        if (parameters.invocationCount < MAX_INVOCATION_COUNT && typedChars > RUN_COMPLETION_AFTER_CHARS) {
            startMaxInvocationCountCompletion(parameters, result, newSorter, addedElements)
        }
    }

    private fun sorter(parameters: CompletionParameters, matcher: PrefixMatcher): CompletionSorter {
        return CompletionService.getCompletionService().defaultSorter(parameters, matcher)
    }

    private fun startMaxInvocationCountCompletion(parameters: CompletionParameters,
                                                  result: CompletionResultSet,
                                                  sorter: CompletionSorter,
                                                  alreadyAddedElements: Set<LookupElement>) {
        val updatedParams = parameters
                .withInvocationCount(MAX_INVOCATION_COUNT)
                .withType(parameters.completionType)

        val start = System.currentTimeMillis()
        CompletionService.getCompletionService().getVariantsFromContributors(updatedParams, this, {
            if (it.lookupElement in alreadyAddedElements) return@getVariantsFromContributors

            val element = UnmatchableLookupElement(it.lookupElement)
            InvocationCountOrigin.setInvocationTime(element, MAX_INVOCATION_COUNT)
            wrap(element, it.prefixMatcher, sorter)?.let { result.passResult(it) }
        })
        val end = System.currentTimeMillis()

        parameters.language()?.registerSecondCompletionContributorsTime(end - start)
    }

}


private fun Language.registerCompletionContributorsTime(time: Long) {
    ContributorsTimeStatistics.getInstance().registerCompletionContributorsTime(this, time)
}


private fun Language.registerSecondCompletionContributorsTime(time: Long) {
    ContributorsTimeStatistics.getInstance().registerSecondCompletionContributorsTime(this, time)
}


object InvocationCountOrigin {

    private val ORIGIN_KEY = Key.create<Int>("second.completion.run")

    fun setInvocationTime(element: LookupElement, number: Int) {
        element.putUserData(ORIGIN_KEY, number)
    }

    fun invocationTime(element: LookupElement): Int = element.getUserData(ORIGIN_KEY) ?: 0

}


class CompletionNumberWeigher : LookupElementWeigher("completion.number.weigher") {

    override fun weigh(element: LookupElement): Comparable<Nothing> {
        val weigh = InvocationCountOrigin.invocationTime(element)
        return weigh
    }

}