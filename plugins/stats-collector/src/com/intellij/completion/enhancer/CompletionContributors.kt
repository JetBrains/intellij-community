// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.completion.enhancer

import com.intellij.codeInsight.completion.CompletionContributorEP
import com.intellij.openapi.extensions.ExtensionPoint
import com.intellij.openapi.extensions.Extensions
import com.intellij.openapi.extensions.LoadingOrder
import com.intellij.openapi.extensions.impl.ExtensionComponentAdapter
import com.intellij.util.ReflectionUtil

internal object CompletionContributors {
  fun addFirst(contributorEP: CompletionContributorEP) {
    val extensionPoint = extensionPoint()
    val first = extensionPoint.extensions.first() as CompletionContributorEP
    val id = contributorOrderId(first)
    val order = LoadingOrder.readOrder("first, before $id")
    extensionPoint.registerExtension(contributorEP, order)
  }

  fun extensionPoint(): ExtensionPoint<CompletionContributorEP> {
    return Extensions.getRootArea().getExtensionPoint<CompletionContributorEP>("com.intellij.completion.contributor")
  }

  private fun contributorOrderId(contributorEP: CompletionContributorEP): String? {
    val className = contributorEP.implementationClass
    val picoContainer = Extensions.getRootArea().picoContainer
    val adapterForFirstContributor = (picoContainer.componentAdapters)
      .asSequence()
      .filterIsInstance<ExtensionComponentAdapter>()
      .filter { ReflectionUtil.isAssignable(CompletionContributorEP::class.java, it.componentImplementation) }
      .map { it to it.getComponentInstance(picoContainer) as? CompletionContributorEP }
      .find { it.second?.implementationClass == className }?.first

    return adapterForFirstContributor?.orderId
  }
}