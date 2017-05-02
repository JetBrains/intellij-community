package com.intellij.sorting

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.LightFixtureCompletionTestCase
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.sorting.Samples.classNameCompletion
import com.intellij.sorting.Samples.classText
import com.intellij.sorting.Samples.methodCompletion
import com.intellij.stats.completion.ResponseData
import com.intellij.stats.completion.experiment.WebServiceStatus
import org.assertj.core.api.Assertions.assertThat


class CompletionOrderTest : LightFixtureCompletionTestCase() {

    lateinit var ranker: Ranker

    override fun setUp() {
        super.setUp()
        ranker = Ranker.getInstance()

        DumbRequestService.onAnyRequestReturn = ResponseData(200, """{
  "status" : "ok",
  "salt":"a777b8ad",
  "experimentVersion":2,
  "url": "http://test.jetstat-resty.aws.intellij.net/uploadstats",
  "urlForZipBase64Content": "http://test.jetstat-resty.aws.intellij.net/uploadstats/compressed",
  "performExperiment": true
}
""")

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

        lookup.checkMlRanking(ranker, prefix_length = 0)

        myFixture.type('t')
        lookup.checkMlRanking(ranker, prefix_length = 1)

        myFixture.type('e')
        lookup.checkMlRanking(ranker, prefix_length = 2)

        myFixture.type('s')
        lookup.checkMlRanking(ranker, prefix_length = 3)
    }

}