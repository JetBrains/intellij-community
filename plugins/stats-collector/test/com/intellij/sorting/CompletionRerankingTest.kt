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
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.mocks.FakeRanker
import com.intellij.mocks.TestRequestService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ServiceManager
import com.intellij.stats.completion.experiment.WebServiceStatus
import com.intellij.testFramework.registerServiceInstance
import org.assertj.core.api.Assertions.assertThat


private val testInterface = """
interface Whatever {
    void checkMe() {}
    void check() {}
    void checkYou() {}
    void checkWhatever() {}
}
"""
    

private val text = """
class Test {
    public void run() {
        Whatever w;
        w.<caret>
    }
}
"""


class CompletionRerankingTest : LightFixtureCompletionTestCase() {

    private lateinit var fakeRanker: FakeRanker
    private lateinit var realRanker: Ranker
    
    override fun setUp() {
        super.setUp()

        realRanker = ServiceManager.getService(Ranker::class.java)
        fakeRanker = FakeRanker()
        ApplicationManager.getApplication().registerServiceInstance(Ranker::class.java, fakeRanker)
        
        myFixture.addClass(testInterface)
        myFixture.configureByText(JavaFileType.INSTANCE, text)

        TestRequestService.mock = WebServiceMock.mockRequestService(performExperiment = true)
        WebServiceStatus.getInstance().updateStatus()
    }

    override fun tearDown() {
        ApplicationManager.getApplication().registerServiceInstance(Ranker::class.java, realRanker)
        super.tearDown()
    }

    fun `test ensure items sorted on first time and resorted on each typing`() {
        fakeRanker.isShortFirst = true
        myFixture.completeBasic()
        assertThat(myFixture.lookupElementStrings).isEqualTo(listOf(
                "check",
                "checkMe",
                "checkYou",
                "checkWhatever"
        ))
        
        fakeRanker.isShortFirst = false
        myFixture.type('c')
        assertThat(myFixture.lookupElementStrings).isEqualTo(listOf(
                "checkWhatever",
                "checkYou",
                "checkMe",
                "check"
        ))
    }

}


