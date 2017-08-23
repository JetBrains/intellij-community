package com.intellij.completion.contributors

import com.intellij.codeInsight.completion.CompletionContributorEP
import com.intellij.completion.enhancer.CompletionContributors
import com.intellij.completion.enhancer.InvocationCountEnhancingContributor
import com.intellij.openapi.extensions.Extensions
import com.intellij.testFramework.PlatformTestCase
import org.assertj.core.api.Assertions.assertThat


class ContributorsOrderTest: PlatformTestCase() {

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
        CompletionContributors.removeFirst()
        super.tearDown()
    }

    fun `test invocation enhancing contributor is first`() {
        val first = CompletionContributors.first()
        assertThat(first.implementationClass).isEqualTo(enhancerClassName)
    }

}