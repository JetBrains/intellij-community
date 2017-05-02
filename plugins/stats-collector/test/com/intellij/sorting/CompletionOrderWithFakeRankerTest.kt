package com.intellij.sorting

import com.intellij.codeInsight.completion.LightFixtureCompletionTestCase
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.extensions.ExtensionPoint
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.Extensions
import com.intellij.openapi.extensions.LoadingOrder
import com.intellij.openapi.util.Pair
import com.intellij.psi.WeigherExtensionPoint
import com.jetbrains.completion.ranker.features.FeatureUtils
import org.assertj.core.api.Assertions


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

        assertEachItemHasMlValue(FeatureUtils.UNDEFINED)
        assertNormalItemsOrder()
    }


    fun `test do not rank if decision says do not rank`() {
        myFixture.addClass(Samples.callCompletionOnClass)
        myFixture.configureByText(JavaFileType.INSTANCE, Samples.methodCompletion)

        TestExperimentDecision.isPerformExperiment = false
        myFixture.completeBasic()

        assertEachItemHasMlValue(FeatureUtils.NONE)
        assertNormalItemsOrder()
    }


    fun `test features with null values are ignored even if unknown and result is sorted`() {
        myFixture.addClass(Samples.callCompletionOnClass)
        FakeWeighter.isReturnNull = true
        myFixture.configureByText(JavaFileType.INSTANCE, Samples.methodCompletion)
        myFixture.completeBasic()

        (myFixture.lookup as LookupImpl).checkMlRanking(ranker, 0)
    }

    private fun assertEachItemHasMlValue(value: String) {
        val lookup = myFixture.lookup as LookupImpl
        val objects: Map<LookupElement, List<Pair<String, Any>>> = lookup.getRelevanceObjects(lookup.items, false)
        val ranks = objects
                .mapNotNull { it.value.find { it.first == FeatureUtils.ML_RANK } }
                .map { it.second }
                .toSet()

        Assertions.assertThat(ranks.size).withFailMessage("Ranks size: ${ranks.size} expected: 1\nRanks $ranks").isEqualTo(1)
        Assertions.assertThat(ranks.first()).isEqualTo(value)
    }

    private fun assertNormalItemsOrder() {
        val lookup = myFixture.lookup
        val items = lookup.items.map { it.lookupString }
        Assertions.assertThat(items).isEqualTo(listOf("qqqq", "runq", "test", "qwrt"))
    }

    private fun fakeWeigher() = WeigherExtensionPoint().apply {
        id = "fake"
        key = "completion"
        implementationClass = "com.intellij.sorting.FakeWeighter"
    }

}

