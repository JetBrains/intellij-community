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

import com.intellij.codeInsight.completion.LightFixtureCompletionTestCase
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.mocks.TestRequestService
import com.intellij.stats.completion.experiment.WebServiceStatus
import com.jetbrains.completion.ranker.features.FeatureUtils
import org.assertj.core.api.Assertions.assertThat


class UpdateExperimentStatusTest: LightFixtureCompletionTestCase() {


    override fun setUp() {
        super.setUp()
    }

    override fun tearDown() {
        super.tearDown()
    }

    fun `test on performExperiment=true sort`() {
        TestRequestService.mock = WebServiceMock.mockRequestService(performExperiment = true)
        WebServiceStatus.getInstance().updateStatus()

        doComplete()

        val lookup = myFixture.lookup as LookupImpl
        assertThat(lookup.items).isNotEmpty()

        lookup.checkMlRanking(Ranker.getInstance(), 1)
    }

    fun `test on performExperiment=false do not sort`() {
        TestRequestService.mock = WebServiceMock.mockRequestService(performExperiment = false)
        WebServiceStatus.getInstance().updateStatus()

        doComplete()

        val lookup = myFixture.lookup as LookupImpl
        assertThat(lookup.items).isNotEmpty()

        lookup.assertEachItemHasMlValue(FeatureUtils.NONE)
    }

    private fun doComplete() {
        myFixture.addClass("public class Test {}")
        myFixture.addClass("public class Tooo {}")
        myFixture.addClass("public class Teee {}")

        val text = """
    class Test {
        void test() {
            T<caret>
        }
    }
    """
        myFixture.configureByText(JavaFileType.INSTANCE, text)
        myFixture.completeBasic()
    }

}