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

package com.intellij.completion.contributors

import com.intellij.codeInsight.completion.CompletionContributorEP
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.LightFixtureCompletionTestCase
import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.completion.enhancer.CompletionContributors
import com.intellij.completion.enhancer.InvocationCountEnhancingContributor
import com.intellij.ide.highlighter.JavaFileType
import org.assertj.core.api.Assertions.assertThat

class InvocationCountEnhancerTest : LightFixtureCompletionTestCase() {

    private lateinit var testContributor: CompletionContributorEP
    private var beforeCharTyped = 0
    private val text = """
                class Test {
                  public void check() {
                    <caret>
                  }
                }
            """.trimIndent()


    override fun setUp() {
        super.setUp()

        TestContributor.isEnabled = true
        InvocationCountEnhancingContributor.isEnabledInTests = true
        beforeCharTyped = InvocationCountEnhancingContributor.RUN_COMPLETION_AFTER_CHARS
        InvocationCountEnhancingContributor.RUN_COMPLETION_AFTER_CHARS = 0

        testContributor = CompletionContributorEP().apply {
            implementationClass = TestContributor::class.java.name
            language = "any"
        }
        CompletionContributorUtils.add(testContributor)

        CompletionContributorEP().apply {
            implementationClass = InvocationCountEnhancingContributor::class.java.name
            language = "any"
        }.let {
            CompletionContributors.addFirst(it)
        }

        myFixture.addClass("class Embedded {}")
        myFixture.addClass("class Elf {}")

        myFixture.configureByText(JavaFileType.INSTANCE, text)
    }

    override fun tearDown() {
        CompletionContributorUtils.remove(testContributor)
        CompletionContributorUtils.removeFirst()

        TestContributor.isEnabled = false
        InvocationCountEnhancingContributor.isEnabledInTests = false
        InvocationCountEnhancingContributor.RUN_COMPLETION_AFTER_CHARS = beforeCharTyped

        super.tearDown()
    }

    fun `test basic completion all elements provided, those from higher invocation count at the bottom`() {
        try {
            InvocationCountEnhancingContributor.isEnabledInTests = false
            myFixture.type('E')
            myFixture.complete(CompletionType.BASIC)
            assertThat(lookup.itemStrings()).isEqualTo(listOf(
                    "EC_BASIC_COUNT_0",
                    "EC_BASIC_COUNT_1",
                    "Elf",
                    "Embedded",
                    "Test"
            ))
            myFixture.type('\b')
        }
        finally {
            InvocationCountEnhancingContributor.isEnabledInTests = true
        }

        myFixture.type('E')
        myFixture.complete(CompletionType.BASIC)
        assertThat(lookup.itemStrings()).isEqualTo(
                listOf(
                        "Elf",
                        "Embedded",
                        "EC_BASIC_COUNT_0",
                        "EC_BASIC_COUNT_1",
                        "Test",
                        "EC_BASIC_COUNT_2",
                        "EC_BASIC_COUNT_3",
                        "EC_BASIC_COUNT_4"
                )
        )
    }

    fun `test class name completion all elements provided, those from higher invocation count at the bottom`() {
        myFixture.type('E')
        myFixture.complete(CompletionType.CLASS_NAME)

        assertThat(lookup.itemStrings()).isEqualTo(
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


private fun Lookup.itemStrings() = (this as LookupImpl).items.map { it.lookupString }