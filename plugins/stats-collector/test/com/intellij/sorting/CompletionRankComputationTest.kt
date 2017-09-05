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

package com.intellij.sorting

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.LightFixtureCompletionTestCase
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.mocks.TestRequestService
import com.intellij.sorting.Samples.classNameCompletion
import com.intellij.sorting.Samples.classText
import com.intellij.sorting.Samples.methodCompletion
import com.intellij.stats.completion.experiment.WebServiceStatus
import com.intellij.stats.completion.prefixLength
import org.assertj.core.api.Assertions.assertThat


class CompletionOrderTest : LightFixtureCompletionTestCase() {

    lateinit var ranker: Ranker

    override fun setUp() {
        super.setUp()
        ranker = Ranker.getInstance()
        TestRequestService.mock = WebServiceMock.mockRequestService(performExperiment = true)
        WebServiceStatus.getInstance().updateStatus()
    }

    fun `test class name completion reranking`() {
        myFixture.addClass("public interface Foo {}")
        myFixture.addClass("public interface Fowo {}")
        myFixture.addClass("package com; public interface Foao {}")
        myFixture.addClass("package com.intellij; public interface Fobo {}")

        myFixture.configureByText(JavaFileType.INSTANCE, classNameCompletion)
        myFixture.complete(CompletionType.BASIC, 2)

        val lookup = myFixture.lookup as LookupImpl
        assertThat(lookup.items.size > 0)

        lookup.checkMlRanking(ranker, prefix_length = 1)
    }

    fun `test normal completion reranking`() {
        myFixture.addClass(classText)

        myFixture.configureByText(JavaFileType.INSTANCE, methodCompletion)
        myFixture.completeBasic()

        val lookup = myFixture.lookup as LookupImpl
        assertThat(lookup.items.size > 0)

        lookup.checkMlRanking(ranker, lookup.prefixLength())

        myFixture.type('t')
        lookup.checkMlRanking(ranker, lookup.prefixLength())

        myFixture.type('e')
        lookup.checkMlRanking(ranker, lookup.prefixLength())

        myFixture.type('s')
        lookup.checkMlRanking(ranker, lookup.prefixLength())
    }

}