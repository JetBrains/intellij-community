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
import com.intellij.mocks.FakeWeighter
import com.intellij.mocks.TestExperimentDecision
import com.intellij.mocks.TestRequestService
import com.intellij.openapi.extensions.ExtensionPoint
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.Extensions
import com.intellij.openapi.extensions.LoadingOrder
import com.intellij.psi.WeigherExtensionPoint
import com.intellij.stats.completion.RequestService
import com.intellij.stats.completion.ResponseData
import com.intellij.stats.completion.experiment.WebServiceStatusProvider
import com.intellij.stats.completion.experiment.WebServiceStatus
import com.jetbrains.completion.ranker.features.FeatureUtils
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import org.assertj.core.api.Assertions
import org.mockito.ArgumentMatchers.anyMap
import org.mockito.ArgumentMatchers.anyString
import java.io.File


object WebServiceMock {

    private fun status(performExperiment: Boolean) = """
{
    "status" : "ok",
    "salt":"a777b8ad",
    "experimentVersion":2,
    "url": "http://url1",
    "urlForZipBase64Content":"http://url2",
    "performExperiment": $performExperiment
}"""

    private val NOT_SUPPOSED_TO_BE_CALLED = IllegalAccessError("NOT SUPPOSED TO BE CALLED")

    fun mockRequestService(performExperiment: Boolean): RequestService {
        val response = ResponseData(200, status(performExperiment))
        return mock {
            on { get(anyString()) }.thenAnswer {
                val url = it.arguments.first() as String
                if (url == WebServiceStatusProvider.STATUS_URL) response else throw NOT_SUPPOSED_TO_BE_CALLED
            }
            on { post(anyString(), anyMap()) }.thenThrow(NOT_SUPPOSED_TO_BE_CALLED)
            on { postZipped(anyString(), any()) }.thenThrow(NOT_SUPPOSED_TO_BE_CALLED)
            on { post(anyString(), any<File>()) }.thenThrow(NOT_SUPPOSED_TO_BE_CALLED)
        }
    }


}


class CompletionOrderWithFakeRankerTest : LightFixtureCompletionTestCase() {

    lateinit var ranker: Ranker
    lateinit var point: ExtensionPoint<WeigherExtensionPoint>
    lateinit var fakeWeigherExt: WeigherExtensionPoint

    override fun setUp() {
        super.setUp()
        ranker = Ranker.getInstance()
        fakeWeigherExt = fakeWeigher()

        val name = ExtensionPointName<WeigherExtensionPoint>("com.intellij.weigher")
        point = Extensions.getRootArea().getExtensionPoint(name)
        point.registerExtension(fakeWeigherExt, LoadingOrder.before("templates"))

        TestRequestService.mock = WebServiceMock.mockRequestService(performExperiment = true)
        WebServiceStatus.getInstance().updateStatus()
    }

    override fun tearDown() {
        TestExperimentDecision.isPerformExperiment = true
        FakeWeighter.isReturnNull = false
        point.unregisterExtension(fakeWeigherExt)
        super.tearDown()
    }

    fun `test do not rerank if encountered unknown features`() {
        myFixture.addClass(Samples.callCompletionOnClass)
        myFixture.configureByText(JavaFileType.INSTANCE, Samples.methodCompletion)

        myFixture.completeBasic()

        (myFixture.lookup as LookupImpl).assertEachItemHasMlValue(FeatureUtils.UNDEFINED)
        assertNormalItemsOrder()
    }


    fun `test do not rank if decision says do not rank`() {
        myFixture.addClass(Samples.callCompletionOnClass)
        myFixture.configureByText(JavaFileType.INSTANCE, Samples.methodCompletion)

        TestExperimentDecision.isPerformExperiment = false
        myFixture.completeBasic()

        (myFixture.lookup as LookupImpl).assertEachItemHasMlValue(FeatureUtils.NONE)
        assertNormalItemsOrder()
    }


    fun `test features with null values are ignored even if unknown and result is sorted`() {
        myFixture.addClass(Samples.callCompletionOnClass)
        FakeWeighter.isReturnNull = true
        myFixture.configureByText(JavaFileType.INSTANCE, Samples.methodCompletion)
        myFixture.completeBasic()

        (myFixture.lookup as LookupImpl).checkMlRanking(ranker, 0)
    }


    private fun assertNormalItemsOrder() {
        val lookup = myFixture.lookup
        val items = lookup.items.map { it.lookupString }
        Assertions.assertThat(items).isEqualTo(listOf("qqqq", "runq", "test", "qwrt"))
    }

    private fun fakeWeigher() = WeigherExtensionPoint().apply {
        id = "fake"
        key = "completion"
        implementationClass = "com.intellij.mocks.FakeWeighter"
    }

}

