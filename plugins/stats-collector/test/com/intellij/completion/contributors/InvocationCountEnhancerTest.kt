package com.intellij.completion.contributors

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.completion.enhancer.CompletionContributors
import com.intellij.completion.enhancer.InvocationCountEnhancingContributor
import com.intellij.ide.highlighter.JavaFileType
import org.assertj.core.api.Assertions.assertThat


class InvocationCountEnhancerTest : LightFixtureCompletionTestCase() {

    private lateinit var testContributor: CompletionContributorEP
    private val text = """
                class Test {
                  public void check() {
                    <caret>
                  }
                }
            """.trimIndent()


    override fun setUp() {
        super.setUp()
        testContributor = CompletionContributorEP().apply {
            implementationClass = TestContributor::class.java.name
            language = "any"
        }
        CompletionContributors.add(testContributor)

        CompletionContributorEP().apply {
            implementationClass = InvocationCountEnhancingContributor::class.java.name
            language = "any"
        }.let {
            CompletionContributors.addFirst(it)
        }

        myFixture.addClass("class Embedded {}")
        myFixture.addClass("class Elf {}")

        myFixture.configureByText(JavaFileType.INSTANCE, text)
        myFixture.type('E')
    }

    override fun tearDown() {
        CompletionContributors.remove(testContributor)
        CompletionContributors.removeFirst()
        super.tearDown()
    }

    fun `test basic completion all elements provided, those from higher invocation count at the bottom`() {
        myFixture.complete(CompletionType.BASIC)

        val elements = (lookup as LookupImpl).items
        assertThat(elements.map { it.lookupString }).isEqualTo(
                listOf(
                        "EC_BASIC_COUNT_0",
                        "EC_BASIC_COUNT_1",
                        "Elf",
                        "Embedded",
                        "Test",
                        "EC_BASIC_COUNT_2",
                        "EC_BASIC_COUNT_3",
                        "EC_BASIC_COUNT_4"
                )
        )
    }

    fun `test class name completion all elements provided, those from higher invocation count at the bottom`() {
        myFixture.complete(CompletionType.CLASS_NAME)

        val elements = (lookup as LookupImpl).items
        assertThat(elements.map { it.lookupString }).isEqualTo(
                listOf(
                        "Elf",
                        "Embedded",
                        "EC_CLASS_NAME_COUNT_0",
                        "EC_CLASS_NAME_COUNT_1",
                        "Test",
                        "EC_CLASS_NAME_COUNT_2",
                        "EC_CLASS_NAME_COUNT_3",
                        "EC_CLASS_NAME_COUNT_4"
                )
        )
    }

}
