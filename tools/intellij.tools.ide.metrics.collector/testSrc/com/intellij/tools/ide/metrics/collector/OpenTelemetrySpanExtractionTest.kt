package com.intellij.tools.ide.metrics.collector

import com.intellij.tools.ide.metrics.collector.metrics.PerformanceMetrics.Metric
import com.intellij.tools.ide.metrics.collector.telemetry.SpanFilter
import com.intellij.tools.ide.metrics.collector.telemetry.getMetricsBasedOnDiffBetweenSpans
import com.intellij.tools.ide.metrics.collector.telemetry.getMetricsForStartup
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Paths
import kotlin.io.path.div

internal val openTelemetryReports by lazy {
  Paths.get(OpenTelemetrySpanExtractionTest::class.java.classLoader.getResource("opentelemetry")!!.toURI())
}

class OpenTelemetrySpanExtractionTest {

  @Test
  fun startupMetricsCollected() {
    val file = (openTelemetryReports / "startup.json")
    val result = getMetricsForStartup(file)
    result.shouldHaveSize(557)
    result.shouldContain(Metric.newDuration("bootstrap", 58))
    result.shouldContain(Metric.newDuration("startApplication", 3035))
    result.shouldContain(Metric.newDuration("status bar pre-init", 136))
    result.shouldContain(Metric.newDuration("status bar pre-init.start", 1))
    result.shouldContain(Metric.newDuration("status bar pre-init.end", 1 + 136))
    result.filter { it.id.name.contains(": scheduling") }.shouldHaveSize(0)
    result.filter { it.id.name.contains(": completing") }.shouldHaveSize(0)
  }

  @Test
  fun testTagsFilter() {
    val result = OpenTelemetrySpanCollector(
      SpanFilter.hasTags(Pair("class", "com.intellij.diagnostic.startUpPerformanceReporter.StartUpPerformanceReporter"), Pair("plugin", "com.intellij"))
    ).collect(openTelemetryReports / "opentelemetry.json")

    result.shouldContainExactlyInAnyOrder(Metric.newDuration("run activity", 0))
  }

  @Test
  fun testContainsInFilter() {
    val spanNames = listOf("%findUsages", "run activity")
    val file = (openTelemetryReports / "opentelemetry.json")
    val expected = spanNames.map { spanName ->
      OpenTelemetrySpanCollector(SpanFilter.nameEquals(spanName)).collect(file)
    }.flatten()

    val result = OpenTelemetrySpanCollector(SpanFilter.containsNameIn(spanNames))
      .collect(file)

    result.shouldContainExactlyInAnyOrder(expected)
  }

  @Test
  fun metricsCorrectlyCollected() {
    val metrics = OpenTelemetrySpanCollector(SpanFilter.nameEquals("%findUsages")).collect(openTelemetryReports / "opentelemetry.json")
    assertThat(metrics).containsAll(listOf(
      Metric.newDuration("%findUsages_1", 530),
      Metric.newDuration("%findUsages_2", 4109),
      Metric.newDuration("%findUsages_3", 3090),
      Metric.newDuration("%findUsages", 7729),
      Metric.newCounter("%findUsages#count", 3),
      Metric.newDuration("%findUsages#mean_value", 2576),
      Metric.newDuration("%findUsages#standard_deviation", 1505),
      Metric.newDuration("FindUsagesManager.startProcessUsages_1", 509),
      Metric.newCounter("FindUsagesManager.startProcessUsages_1#number_of_found_usages", 1),
      Metric.newDuration("FindUsagesManager.startProcessUsages_2", 4106),
      Metric.newCounter("FindUsagesManager.startProcessUsages_2#number_of_found_usages", 549),
      Metric.newDuration("FindUsagesManager.startProcessUsages_3", 3088),
      Metric.newCounter("FindUsagesManager.startProcessUsages_3#number_of_found_usages", 844),
      Metric.newDuration("FindUsagesManager.startProcessUsages", 7703),
      Metric.newCounter("FindUsagesManager.startProcessUsages#count", 3),
      Metric.newDuration("FindUsagesManager.startProcessUsages#mean_value", 2567),
      Metric.newDuration("FindUsagesManager.startProcessUsages#standard_deviation", 1513),
      Metric.newCounter("FindUsagesManager.startProcessUsages#number_of_found_usages#count", 3),
      Metric.newDuration("FindUsagesManager.startProcessUsages#number_of_found_usages#mean_value", 464),
      Metric.newDuration("FindUsagesManager.startProcessUsages#number_of_found_usages#standard_deviation", 349),
    ))
  }

  @Test
  fun metricsWithSingleSpan() {
    val metrics = OpenTelemetrySpanCollector(SpanFilter.nameEquals("performance_test"))
      .collect(openTelemetryReports / "opentelemetry_with_main_timer.json")
    assertThat(metrics).containsAll(listOf(
      Metric.newDuration("performance_test", 13497),
      Metric.newDuration("delayType", 3739),
      Metric.newCounter("test#max_awt_delay", 141),
      Metric.newCounter("test#average_awt_delay", 8),
    ))
  }

  @Test
  fun metricsCorrectlyCollected2() {
    val metrics = OpenTelemetrySpanCollector(SpanFilter.nameEquals("performance_test"))
      .collect(openTelemetryReports / "opentelemetry2.json")

    assertThat(metrics).containsAll(listOf(
      Metric.newDuration("performance_test", 81444),
      Metric.newDuration("timer_1", 1184),
      Metric.newCounter("timer_1#average_awt_delay", 3),
      Metric.newCounter("timer_1#max_awt_delay", 57),
      Metric.newDuration("timer_2", 1519),
      Metric.newCounter("timer_2#average_awt_delay", 7),
      Metric.newCounter("timer_2#max_awt_delay", 84),
      Metric.newDuration("timer", 2703),
      Metric.newCounter("timer#count", 2),
      Metric.newDuration("timer#mean_value", 1351),
      Metric.newDuration("timer#standard_deviation", 167),
      Metric.newDuration("findUsages_1", 1204),
      Metric.newDuration("findUsages_2", 1183),
      Metric.newCounter("findUsages_2#number_of_found_usages", 1384),
      Metric.newDuration("findUsages#standard_deviation", 10),
      Metric.newDuration("findUsages", 2387),
      Metric.newCounter("findUsages#count", 2),
      Metric.newDuration("findUsages#mean_value", 1193),
      Metric.newCounter("timer#max_awt_delay#count", 2),
      Metric.newDuration("timer#max_awt_delay#mean_value", 70),
      Metric.newDuration("timer#max_awt_delay#standard_deviation", 13),
      Metric.newCounter("findUsages#number_of_found_usages#count", 1),
      Metric.newDuration("findUsages#number_of_found_usages#mean_value", 1384),
      Metric.newDuration("findUsages#number_of_found_usages#standard_deviation", 0),
      Metric.newCounter("timer#average_awt_delay#count", 2),
      Metric.newDuration("timer#average_awt_delay#mean_value", 5),
      Metric.newDuration("timer#average_awt_delay#standard_deviation", 2),
    ))
  }

  @Test
  fun metricsCorrectlyCollectedAvoidingZeroValue() {
    val metrics = OpenTelemetrySpanCollector(SpanFilter.nameEquals("performance_test"))
      .collect(openTelemetryReports / "opentelemetry_with_zero_values.json")

    assertThat(metrics).containsAll(listOf(
      Metric.newDuration("performance_test", 27989),
      Metric.newDuration("firstCodeAnalysis", 1725),
      Metric.newDuration("typing_1", 157),
      Metric.newDuration("typing_2", 43),
      Metric.newDuration("typing_3", 5),
      Metric.newDuration("typing_4", 2),
      Metric.newDuration("typing_5", 3),
      Metric.newDuration("typing_6", 3),
      Metric.newDuration("typing", 213),
      Metric.newCounter("typing#count", 6),
      Metric.newDuration("typing#mean_value", 35),
      Metric.newDuration("typing#standard_deviation", 56),
      Metric.newDuration("completion_1", 539),
      Metric.newCounter("completion_1#number", 635),
      Metric.newDuration("completion_2", 148),
      Metric.newCounter("completion_2#number", 635),
      Metric.newDuration("completion_3", 130),
      Metric.newCounter("completion_3#number", 635),
      Metric.newDuration("completion_4", 116),
      Metric.newCounter("completion_4#number", 635),
      Metric.newDuration("completion_5", 109),
      Metric.newCounter("completion_5#number", 635),
      Metric.newCounter("completion#number#count", 5),
      Metric.newDuration("completion#number#mean_value", 635),
      Metric.newDuration("completion#number#standard_deviation", 0),
      Metric.newDuration("completion", 1042),
      Metric.newCounter("completion#count", 5),
      Metric.newDuration("completion#mean_value", 208),
      Metric.newDuration("completion#standard_deviation", 165),
      Metric.newDuration("invokeCompletion_1", 542),
      Metric.newCounter("invokeCompletion_1#caretOffset", 270),
      Metric.newDuration("invokeCompletion_2", 149),
      Metric.newCounter("invokeCompletion_2#caretOffset", 270),
      Metric.newDuration("invokeCompletion_3", 131),
      Metric.newCounter("invokeCompletion_3#caretOffset", 270),
      Metric.newDuration("invokeCompletion_4", 116),
      Metric.newCounter("invokeCompletion_4#caretOffset", 270),
      Metric.newDuration("invokeCompletion_5", 109),
      Metric.newCounter("invokeCompletion_5#caretOffset", 270),
      Metric.newCounter("invokeCompletion#caretOffset#count", 5),
      Metric.newDuration("invokeCompletion#caretOffset#mean_value", 270),
      Metric.newDuration("invokeCompletion#caretOffset#standard_deviation", 0),
      Metric.newDuration("invokeCompletion", 1047),
      Metric.newCounter("invokeCompletion#count", 5),
      Metric.newDuration("invokeCompletion#mean_value", 209),
      Metric.newDuration("invokeCompletion#standard_deviation", 166),
      Metric.newDuration("performCompletion_1", 302),
      Metric.newCounter("performCompletion_1#lookupsFound", 635),
      Metric.newDuration("performCompletion_2", 59),
      Metric.newCounter("performCompletion_2#lookupsFound", 635),
      Metric.newDuration("performCompletion_3", 63),
      Metric.newCounter("performCompletion_3#lookupsFound", 635),
      Metric.newDuration("performCompletion_4", 49),
      Metric.newCounter("performCompletion_4#lookupsFound", 635),
      Metric.newDuration("performCompletion_5", 50),
      Metric.newCounter("performCompletion_5#lookupsFound", 635),
      Metric.newCounter("performCompletion#lookupsFound#count", 5),
      Metric.newDuration("performCompletion#lookupsFound#mean_value", 635),
      Metric.newDuration("performCompletion#lookupsFound#standard_deviation", 0),
      Metric.newDuration("performCompletion", 523),
      Metric.newCounter("performCompletion#count", 5),
      Metric.newDuration("performCompletion#mean_value", 104),
      Metric.newDuration("performCompletion#standard_deviation", 98),
      Metric.newDuration("ComboEditorCompletionContributor", 2),
      Metric.newDuration("ContextFeaturesContributor_1", 16),
      Metric.newDuration("ContextFeaturesContributor_2", 1),
      Metric.newDuration("ContextFeaturesContributor_3", 0),
      Metric.newDuration("ContextFeaturesContributor_4", 0),
      Metric.newDuration("ContextFeaturesContributor_5", 0),
      Metric.newDuration("ContextFeaturesContributor", 17),
      Metric.newCounter("ContextFeaturesContributor#count", 5),
      Metric.newDuration("ContextFeaturesContributor#mean_value", 3),
      Metric.newDuration("ContextFeaturesContributor#standard_deviation", 6),
      Metric.newDuration("LiveTemplateCompletionContributor_1", 273),
      Metric.newDuration("LiveTemplateCompletionContributor_2", 58),
      Metric.newDuration("LiveTemplateCompletionContributor_3", 62),
      Metric.newDuration("LiveTemplateCompletionContributor_4", 48),
      Metric.newDuration("LiveTemplateCompletionContributor_5", 49),
      Metric.newDuration("LiveTemplateCompletionContributor", 490),
      Metric.newCounter("LiveTemplateCompletionContributor#count", 5),
      Metric.newDuration("LiveTemplateCompletionContributor#mean_value", 98),
      Metric.newDuration("LiveTemplateCompletionContributor#standard_deviation", 87),
      Metric.newDuration("FilePathCompletionContributor", 0),
      Metric.newDuration("UrlPathReferenceCompletionContributor", 3),
      Metric.newDuration("PhpNamedArgumentsCompletionContributor", 9),
      Metric.newDuration("PhpKeywordsCompletionContributor", 5),
      Metric.newDuration("PhpCompletionContributor_1", 194),
      Metric.newDuration("PhpCompletionContributor_2", 55),
      Metric.newDuration("PhpCompletionContributor_3", 60),
      Metric.newDuration("PhpCompletionContributor_4", 46),
      Metric.newDuration("PhpCompletionContributor_5", 47),
      Metric.newDuration("PhpCompletionContributor", 402),
      Metric.newCounter("PhpCompletionContributor#count", 5),
      Metric.newDuration("PhpCompletionContributor#mean_value", 80),
      Metric.newDuration("PhpCompletionContributor#standard_deviation", 57),
      Metric.newDuration("CssClassOrIdReferenceCompletionContributor", 1),
      Metric.newDuration("LegacyCompletionContributor", 0),
      Metric.newDuration("arrangeItems_1", 70),
      Metric.newDuration("arrangeItems_2", 43),
      Metric.newDuration("arrangeItems_3", 20),
      Metric.newDuration("arrangeItems_4", 14),
      Metric.newDuration("arrangeItems_5", 12),
      Metric.newDuration("arrangeItems_6", 12),
      Metric.newDuration("arrangeItems_7", 11),
      Metric.newDuration("arrangeItems_8", 10),
      Metric.newDuration("arrangeItems_9", 10),
      Metric.newDuration("arrangeItems_10", 11),
      Metric.newDuration("arrangeItems_11", 11),
      Metric.newDuration("arrangeItems_12", 12),
      Metric.newDuration("arrangeItems_13", 10),
      Metric.newDuration("arrangeItems_14", 9),
      Metric.newDuration("arrangeItems_15", 9),
      Metric.newDuration("arrangeItems", 264),
      Metric.newCounter("arrangeItems#count", 15),
      Metric.newDuration("arrangeItems#mean_value", 17),
      Metric.newDuration("arrangeItems#standard_deviation", 16),
      Metric.newCounter("test#max_awt_delay", 714),
      Metric.newCounter("test#average_awt_delay", 7),
    ))
  }

  @Test
  fun metricsWithAttributesMaxAndMeanValue() {
    val metrics = OpenTelemetrySpanCollector(SpanFilter.nameEquals("performance_test"))
      .collect(openTelemetryReports / "opentelemetry_with_max_mean_attributes.json")

    assertThat(metrics).containsAll(listOf(
      Metric.newDuration("typing#latency#max", 51),
      Metric.newDuration("typing#latency#mean_value", 3),
    ))
  }

  @Test
  fun opentelemetryWithWarmupSpans() {
    val metrics = OpenTelemetrySpanCollector(SpanFilter.nameEquals("performance_test"))
      .collect(openTelemetryReports / "opentelemetry_with_warmup_spans.json")

    assertThat(metrics).containsAll(listOf(
      Metric.newDuration("localInspections#mean_value", 368),
      Metric.newDuration("localInspections_1", 375),
      Metric.newDuration("localInspections_2", 372),
      Metric.newDuration("localInspections_3", 367),
      Metric.newDuration("localInspections_4", 361),
      Metric.newDuration("localInspections_5", 369),
      Metric.newDuration("localInspections", 1844),
      Metric.newDuration("localInspections#Warnings#mean_value", 4),
      Metric.newCounter("localInspections_1#Warnings", 4),
    ))
    val find = metrics.find { it.id.name == "localInspections_6" }
    assert(find == null) {
      "Must be 5 localInspections"
    }

  }

  @Test
  fun diffBetweenMetrics() {
    val metrics = getMetricsBasedOnDiffBetweenSpans("semanticHighlighting",
                                                    (openTelemetryReports / "opentelemetry_with_warmup_spans.json"),
                                                    "localInspections", "GeneralHighlightingPass")
    assertThat(metrics).containsAll(listOf(
      Metric.newDuration("semanticHighlighting_1", 33),
      Metric.newDuration("semanticHighlighting_2", 34),
      Metric.newDuration("semanticHighlighting_3", 35),
      Metric.newDuration("semanticHighlighting_4", 45),
      Metric.newDuration("semanticHighlighting_5", 47),
      Metric.newDuration("semanticHighlighting", 194),
      Metric.newDuration("semanticHighlighting#mean_value", 38),
    ))
    val find = metrics.find { it.id.name == "semanticHighlighting_6" }
    assert(find == null) {
      "Must be 5 semanticHighlighting"
    }
  }

  @Test
  fun findUsageWithWarmUp() {
    val metrics = OpenTelemetrySpanCollector(SpanFilter.nameEquals("performance_test"))
      .collect(openTelemetryReports / "opentelemetry_findUsage_with_warmup.json")

    assertThat(metrics).containsAll(listOf(
      Metric.newDuration("findUsagesParent_1", 361),
      Metric.newDuration("findUsagesParent_2", 337),
      Metric.newDuration("findUsagesParent_3", 335),
      Metric.newDuration("findUsagesParent_4", 319),
      Metric.newDuration("findUsagesParent_5", 349),
      Metric.newDuration("findUsagesParent_6", 341),
      Metric.newDuration("findUsagesParent_7", 339),
      Metric.newDuration("findUsagesParent_8", 289),
      Metric.newDuration("findUsagesParent_9", 325),
      Metric.newDuration("findUsagesParent_10", 340),
    ))
    val find = metrics.find { it.id.name == "findUsagesParent_11" }
    assert(find == null) {
      "Must be 10 findUsagesParent"
    }

  }

  @Test
  fun metricAliases() {
    val aliases = mapOf(Pair("findUsagesParent", "alias"))
    val metrics = OpenTelemetrySpanCollector(SpanFilter.nameEquals("performance_test"), spanAliases = aliases)
      .collect(openTelemetryReports / "opentelemetry_findUsage_with_warmup.json")

    assertThat(metrics).containsAll(listOf(
      Metric.newDuration("alias", 3335),
      Metric.newDuration("alias_1", 361),
      Metric.newDuration("alias_2", 337),
      Metric.newDuration("alias_3", 335),
      Metric.newDuration("alias_4", 319),
      Metric.newDuration("alias_5", 349),
      Metric.newDuration("alias_6", 341),
      Metric.newDuration("alias_7", 339),
      Metric.newDuration("alias_8", 289),
      Metric.newDuration("alias_9", 325),
      Metric.newDuration("alias_10", 340),
      //Check that the alias applies only to the specified metric
      Metric.newDuration("performance_test", 30661)
    ))
  }
}
