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

import com.intellij.codeInsight.completion.CompletionContributorEP
import com.intellij.openapi.extensions.ExtensionPoint
import com.intellij.openapi.extensions.Extensions
import com.intellij.openapi.extensions.LoadingOrder
import com.intellij.openapi.extensions.impl.ExtensionComponentAdapter
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