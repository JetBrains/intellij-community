// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.completion.contributors

import com.intellij.codeInsight.completion.CompletionContributorEP
import com.intellij.completion.enhancer.CompletionContributors
import com.intellij.completion.enhancer.InvocationCountEnhancingContributor
import com.intellij.testFramework.LightPlatformTestCase
import org.assertj.core.api.Assertions.assertThat

class ContributorsOrderTest : LightPlatformTestCase() {
  private val enhancerClassName = InvocationCountEnhancingContributor::class.java.canonicalName

  override fun setUp() {
    super.setUp()

    CompletionContributorEP().apply {
      implementationClass = enhancerClassName
      language = "any"
    }.let {
      CompletionContributors.addFirst(it)
    }
  }

  override fun tearDown() {
    try {
      removeFirst()
    }
    finally {
      super.tearDown()
    }
  }

  private fun removeFirst() {
    val point = CompletionContributors.extensionPoint()
    val first = point.extensions.first()
    point.unregisterExtension(first)
  }

  fun `test invocation enhancing contributor is first`() {
    val first = CompletionContributors.extensionPoint().extensions.first()
    assertThat(first.implementationClass).isEqualTo(enhancerClassName)
  }
}