package com.intellij.sorting

import com.intellij.codeInsight.completion.LightFixtureCompletionTestCase
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.ide.highlighter.JavaFileType
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