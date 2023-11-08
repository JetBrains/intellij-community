package com.intellij.tools.ide.metrics.collector

import com.intellij.tools.ide.metrics.collector.metrics.PerformanceMetrics.Metric
import com.intellij.tools.ide.metrics.collector.metrics.PerformanceMetrics.MetricId.Counter
import com.intellij.tools.ide.metrics.collector.metrics.PerformanceMetrics.MetricId.Duration
import com.intellij.tools.ide.metrics.collector.telemetry.SpanFilter
import com.intellij.tools.ide.metrics.collector.telemetry.getMetricsBasedOnDiffBetweenSpans
import com.intellij.tools.ide.metrics.collector.telemetry.getMetricsForStartup
import com.intellij.tools.ide.metrics.collector.telemetry.getMetricsFromSpanAndChildren
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import org.junit.jupiter.api.Test
import java.nio.file.Paths
import kotlin.io.path.div


class OpenTelemetryTest {

  private val openTelemetryReports by lazy {
    Paths.get(this::class.java.classLoader.getResource("opentelemetry")!!.toURI())
  }

  @Test
  fun startupMetricsCollected(){
    val file = (openTelemetryReports / "startup.json").toFile()
    val result = getMetricsForStartup(file)
    result.shouldHaveSize(560)
    result.shouldContain(Metric(Duration("bootstrap"), 58))
    result.shouldContain(Metric(Duration("startApplication"), 3036))
    result.shouldContain(Metric(Duration("status bar pre-init"), 136))
    result.shouldContain(Metric(Duration("status bar pre-init.start"), 1584))
    result.shouldContain(Metric(Duration("status bar pre-init.end"), 1584 + 136))
    result.filter { it.id.name.contains(": scheduling") }.shouldHaveSize(0)
    result.filter { it.id.name.contains(": completing") }.shouldHaveSize(0)
  }

  @Test
  fun testContainsInFilter() {
    val spanNames = listOf("%findUsages", "run activity")
    val file = (openTelemetryReports / "opentelemetry.json").toFile()
    val expected = spanNames.map { spanName ->
      getMetricsFromSpanAndChildren(file, SpanFilter.equals(spanName))
    }.flatten()
    val result = getMetricsFromSpanAndChildren(file, SpanFilter.containsIn(spanNames))
    result.shouldContainExactlyInAnyOrder(expected)
  }

  @Test
  fun metricsCorrectlyCollected() {
    val metrics = getMetricsFromSpanAndChildren((openTelemetryReports / "opentelemetry.json").toFile(), SpanFilter.equals("%findUsages"))
    metrics.shouldContainExactlyInAnyOrder(listOf(
      Metric(Duration("%findUsages_1"), 531),
      Metric(Duration("%findUsages_2"), 4110),
      Metric(Duration("%findUsages_3"), 3090),
      Metric(Duration("%findUsages"), 7731),
      Metric(Duration("%findUsages#mean_value"), 2577),
      Metric(Duration("%findUsages#standard_deviation"), 1505),
      Metric(Duration("FindUsagesManager.startProcessUsages_1"), 510),
      Metric(Counter("FindUsagesManager.startProcessUsages_1#number_of_found_usages"), 1),
      Metric(Duration("FindUsagesManager.startProcessUsages_2"), 4107),
      Metric(Counter("FindUsagesManager.startProcessUsages_2#number_of_found_usages"), 549),
      Metric(Duration("FindUsagesManager.startProcessUsages_3"), 3088),
      Metric(Counter("FindUsagesManager.startProcessUsages_3#number_of_found_usages"), 844),
      Metric(Duration("FindUsagesManager.startProcessUsages"), 7705),
      Metric(Duration("FindUsagesManager.startProcessUsages#mean_value"), 2568),
      Metric(Duration("FindUsagesManager.startProcessUsages#standard_deviation"), 1513),
      Metric(Duration("FindUsagesManager.startProcessUsages#number_of_found_usages#mean_value"), 464),
      Metric(Duration("FindUsagesManager.startProcessUsages#number_of_found_usages#standard_deviation"), 349),
    ))
  }

  @Test
  fun metricsWithSingleSpan() {
    val metrics = getMetricsFromSpanAndChildren((openTelemetryReports / "opentelemetry_with_main_timer.json").toFile(),
                                                SpanFilter.equals("performance_test"))
    metrics.shouldContainExactlyInAnyOrder(listOf(
      Metric(Duration("performance_test"), 13497),
      Metric(Duration("delayType"), 3739),
      Metric(Counter("test#max_awt_delay"), 141),
      Metric(Counter("test#average_awt_delay"), 8),
    ))
  }

  @Test
  fun metricsCorrectlyCollected2() {
    val metrics = getMetricsFromSpanAndChildren((openTelemetryReports / "opentelemetry2.json").toFile(),
                                                SpanFilter.equals("performance_test"))
    metrics.shouldContainExactlyInAnyOrder(listOf(
      Metric(Duration("performance_test"), 81444),
      Metric(Duration("timer_1"), 1184),
      Metric(Counter("timer_1#average_awt_delay"), 3),
      Metric(Counter("timer_1#max_awt_delay"), 57),
      Metric(Duration("timer_2"), 1519),
      Metric(Counter("timer_2#average_awt_delay"), 7),
      Metric(Counter("timer_2#max_awt_delay"), 84),
      Metric(Duration("timer"), 2703),
      Metric(Duration("timer#mean_value"), 1351),
      Metric(Duration("timer#standard_deviation"), 167),
      Metric(Duration("findUsages_1"), 1205),
      Metric(Duration("findUsages_2"), 1184),
      Metric(Counter("findUsages_2#number_of_found_usages"), 1384),
      Metric(Duration("findUsages#standard_deviation"), 10),
      Metric(Duration("findUsages"), 2389),
      Metric(Duration("findUsages#mean_value"), 1194),
      Metric(Duration("timer#max_awt_delay#mean_value"), 70),
      Metric(Duration("timer#max_awt_delay#standard_deviation"), 13),
      Metric(Duration("findUsages#number_of_found_usages#mean_value"), 1384),
      Metric(Duration("findUsages#number_of_found_usages#standard_deviation"), 0),
      Metric(Duration("timer#average_awt_delay#mean_value"), 5),
      Metric(Duration("timer#average_awt_delay#standard_deviation"), 2),
    ))
  }

  @Test
  fun metricsCorrectlyCollectedAvoidingZeroValue() {
    val metrics = getMetricsFromSpanAndChildren((openTelemetryReports / "opentelemetry_with_zero_values.json").toFile(),
                                                SpanFilter.equals("performance_test"))
    metrics.shouldContainExactlyInAnyOrder(listOf(
      Metric(Duration("performance_test"), 27990),
      Metric(Duration("firstCodeAnalysis"), 1726),
      Metric(Duration("typing_1"), 158),
      Metric(Duration("typing_2"), 44),
      Metric(Duration("typing_3"), 5),
      Metric(Duration("typing_4"), 3),
      Metric(Duration("typing_5"), 3),
      Metric(Duration("typing_6"), 3),
      Metric(Duration("typing"), 216),
      Metric(Duration("typing#mean_value"), 36),
      Metric(Duration("typing#standard_deviation"), 56),
      Metric(Duration("completion_1"), 539),
      Metric(Counter("completion_1#number"), 635),
      Metric(Duration("completion_2"), 149),
      Metric(Counter("completion_2#number"), 635),
      Metric(Duration("completion_3"), 131),
      Metric(Counter("completion_3#number"), 635),
      Metric(Duration("completion_4"), 116),
      Metric(Counter("completion_4#number"), 635),
      Metric(Duration("completion_5"), 109),
      Metric(Counter("completion_5#number"), 635),
      Metric(Duration("completion#number#mean_value"), 635),
      Metric(Duration("completion#number#standard_deviation"), 0),
      Metric(Duration("completion"), 1044),
      Metric(Duration("completion#mean_value"), 208),
      Metric(Duration("completion#standard_deviation"), 165),
      Metric(Duration("invokeCompletion_1"), 543),
      Metric(Counter("invokeCompletion_1#caretOffset"), 270),
      Metric(Duration("invokeCompletion_2"), 150),
      Metric(Counter("invokeCompletion_2#caretOffset"), 270),
      Metric(Duration("invokeCompletion_3"), 131),
      Metric(Counter("invokeCompletion_3#caretOffset"), 270),
      Metric(Duration("invokeCompletion_4"), 117),
      Metric(Counter("invokeCompletion_4#caretOffset"), 270),
      Metric(Duration("invokeCompletion_5"), 110),
      Metric(Counter("invokeCompletion_5#caretOffset"), 270),
      Metric(Duration("invokeCompletion#caretOffset#mean_value"), 270),
      Metric(Duration("invokeCompletion#caretOffset#standard_deviation"), 0),
      Metric(Duration("invokeCompletion"), 1051),
      Metric(Duration("invokeCompletion#mean_value"), 210),
      Metric(Duration("invokeCompletion#standard_deviation"), 166),
      Metric(Duration("performCompletion_1"), 303),
      Metric(Counter("performCompletion_1#lookupsFound"), 635),
      Metric(Duration("performCompletion_2"), 59),
      Metric(Counter("performCompletion_2#lookupsFound"), 635),
      Metric(Duration("performCompletion_3"), 63),
      Metric(Counter("performCompletion_3#lookupsFound"), 635),
      Metric(Duration("performCompletion_4"), 50),
      Metric(Counter("performCompletion_4#lookupsFound"), 635),
      Metric(Duration("performCompletion_5"), 50),
      Metric(Counter("performCompletion_5#lookupsFound"), 635),
      Metric(Duration("performCompletion#lookupsFound#mean_value"), 635),
      Metric(Duration("performCompletion#lookupsFound#standard_deviation"), 0),
      Metric(Duration("performCompletion"), 525),
      Metric(Duration("performCompletion#mean_value"), 105),
      Metric(Duration("performCompletion#standard_deviation"), 99),
      Metric(Duration("ComboEditorCompletionContributor"), 3),
      Metric(Duration("ContextFeaturesContributor_1"), 17),
      Metric(Duration("ContextFeaturesContributor_2"), 1),
      Metric(Duration("ContextFeaturesContributor_3"), 1),
      Metric(Duration("ContextFeaturesContributor_4"), 1),
      Metric(Duration("ContextFeaturesContributor_5"), 1),
      Metric(Duration("ContextFeaturesContributor"), 21),
      Metric(Duration("ContextFeaturesContributor#mean_value"), 4),
      Metric(Duration("ContextFeaturesContributor#standard_deviation"), 6),
      Metric(Duration("LiveTemplateCompletionContributor_1"), 274),
      Metric(Duration("LiveTemplateCompletionContributor_2"), 58),
      Metric(Duration("LiveTemplateCompletionContributor_3"), 62),
      Metric(Duration("LiveTemplateCompletionContributor_4"), 49),
      Metric(Duration("LiveTemplateCompletionContributor_5"), 49),
      Metric(Duration("LiveTemplateCompletionContributor"), 492),
      Metric(Duration("LiveTemplateCompletionContributor#mean_value"), 98),
      Metric(Duration("LiveTemplateCompletionContributor#standard_deviation"), 87),
      Metric(Duration("FilePathCompletionContributor"), 1),
      Metric(Duration("UrlPathReferenceCompletionContributor"), 3),
      Metric(Duration("PhpNamedArgumentsCompletionContributor"), 9),
      Metric(Duration("PhpKeywordsCompletionContributor"), 5),
      Metric(Duration("PhpCompletionContributor_1"), 194),
      Metric(Duration("PhpCompletionContributor_2"), 56),
      Metric(Duration("PhpCompletionContributor_3"), 60),
      Metric(Duration("PhpCompletionContributor_4"), 47),
      Metric(Duration("PhpCompletionContributor_5"), 47),
      Metric(Duration("PhpCompletionContributor"), 404),
      Metric(Duration("PhpCompletionContributor#mean_value"), 80),
      Metric(Duration("PhpCompletionContributor#standard_deviation"), 56),
      Metric(Duration("CssClassOrIdReferenceCompletionContributor"), 2),
      Metric(Duration("LegacyCompletionContributor"), 1),
      Metric(Duration("arrangeItems_1"), 70),
      Metric(Duration("arrangeItems_2"), 43),
      Metric(Duration("arrangeItems_3"), 21),
      Metric(Duration("arrangeItems_4"), 15),
      Metric(Duration("arrangeItems_5"), 13),
      Metric(Duration("arrangeItems_6"), 12),
      Metric(Duration("arrangeItems_7"), 12),
      Metric(Duration("arrangeItems_8"), 10),
      Metric(Duration("arrangeItems_9"), 11),
      Metric(Duration("arrangeItems_10"), 12),
      Metric(Duration("arrangeItems_11"), 11),
      Metric(Duration("arrangeItems_12"), 12),
      Metric(Duration("arrangeItems_13"), 10),
      Metric(Duration("arrangeItems_14"), 9),
      Metric(Duration("arrangeItems_15"), 10),
      Metric(Duration("arrangeItems"), 271),
      Metric(Duration("arrangeItems#mean_value"), 18),
      Metric(Duration("arrangeItems#standard_deviation"), 16),
      Metric(Counter("test#max_awt_delay"), 714),
      Metric(Counter("test#average_awt_delay"), 7),
    ))
  }

  @Test
  fun metricsWithAttributesMaxAndMeanValue() {
    val metrics = getMetricsFromSpanAndChildren((openTelemetryReports / "opentelemetry_with_max_mean_attributes.json").toFile(),
                                                SpanFilter.equals("performance_test"))
    metrics.shouldContainAll(listOf(
      Metric(Duration("typing#latency#max"), 51),
      Metric(Duration("typing#latency#mean_value"), 3),
    ))
  }

  @Test
  fun opentelemetryWithWarmupSpans() {
    val metrics = getMetricsFromSpanAndChildren((openTelemetryReports / "opentelemetry_with_warmup_spans.json").toFile(),
                                                SpanFilter.equals("performance_test"))
    metrics.shouldContainAll(listOf(
      Metric(Duration("localInspections#mean_value"), 369),
      Metric(Duration("localInspections_1"), 375),
      Metric(Duration("localInspections_2"), 373),
      Metric(Duration("localInspections_3"), 367),
      Metric(Duration("localInspections_4"), 361),
      Metric(Duration("localInspections_5"), 370),
      Metric(Duration("localInspections"), 1846),
      Metric(Duration("localInspections#Warnings#mean_value"), 4),
      Metric(Counter("localInspections_1#Warnings"), 4),
    ))
    val find = metrics.find { it.id.name == "localInspections_6" }
    assert(find == null) {
      "Must be 5 localInspections"
    }

  }

  @Test
  fun diffBetweenMetrics() {
    val metrics = getMetricsBasedOnDiffBetweenSpans("semanticHighlighting",
                                                    (openTelemetryReports / "opentelemetry_with_warmup_spans.json").toFile(),
                                                    "localInspections", "GeneralHighlightingPass")
    metrics.shouldContainAll(listOf(
      Metric(Duration("semanticHighlighting_1"), 349),
      Metric(Duration("semanticHighlighting_2"), 339),
      Metric(Duration("semanticHighlighting_3"), 341),
      Metric(Duration("semanticHighlighting_4"), 350),
      Metric(Duration("semanticHighlighting_5"), 351),
      Metric(Duration("semanticHighlighting"), 1730),
      Metric(Duration("semanticHighlighting#mean_value"), 346),
    ))
    val find = metrics.find { it.id.name == "semanticHighlighting_6" }
    assert(find == null) {
      "Must be 5 semanticHighlighting"
    }
  }

  @Test
  fun findUsageWithWarmUp() {
    val metrics = getMetricsFromSpanAndChildren((openTelemetryReports / "opentelemetry_findUsage_with_warmup.json").toFile(),
                                                SpanFilter.equals("performance_test"))
    metrics.shouldContainAll(listOf(
      Metric(Duration("findUsagesParent_1"), 362),
      Metric(Duration("findUsagesParent_2"), 337),
      Metric(Duration("findUsagesParent_3"), 336),
      Metric(Duration("findUsagesParent_4"), 320),
      Metric(Duration("findUsagesParent_5"), 350),
      Metric(Duration("findUsagesParent_6"), 342),
      Metric(Duration("findUsagesParent_7"), 340),
      Metric(Duration("findUsagesParent_8"), 289),
      Metric(Duration("findUsagesParent_9"), 326),
      Metric(Duration("findUsagesParent_10"), 340),
    ))
    val find = metrics.find { it.id.name == "findUsagesParent_11" }
    assert(find == null) {
      "Must be 10 findUsagesParent"
    }

  }
}