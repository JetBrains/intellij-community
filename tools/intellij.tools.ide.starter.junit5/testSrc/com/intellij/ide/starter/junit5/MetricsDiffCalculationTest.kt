package com.intellij.ide.starter.junit5

import com.intellij.ide.starter.models.IDEStartResult
import com.intellij.ide.starter.runner.IDERunContext
import com.intellij.ide.starter.utils.JarUtils
import com.intellij.tools.ide.metrics.collector.metrics.MetricsSelectionStrategy
import com.intellij.tools.ide.metrics.collector.metrics.PerformanceMetrics
import com.intellij.tools.ide.metrics.collector.starter.collector.StarterTelemetryJsonMeterCollector
import com.intellij.tools.ide.metrics.collector.starter.metrics.MetricsDiffCalculator
import com.intellij.tools.ide.metrics.collector.starter.publishing.MetricsPublisher
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.inspectors.shouldForAll
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import org.mockito.Answers
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.Path

@ExperimentalPathApi
@ExtendWith(MockitoExtension::class)
class MetricsDiffCalculationTest {

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private lateinit var runContextMock: IDERunContext

  @TempDir
  lateinit var testDirectory: Path

  private fun getWorkspaceModelMeterCollector(): StarterTelemetryJsonMeterCollector =
    StarterTelemetryJsonMeterCollector(MetricsSelectionStrategy.LATEST) {
      it.name.startsWith("jps.") || it.name.startsWith("workspaceModel.")
    }

  private fun getMetricsPublisher(): MetricsPublisher<Any> = object : MetricsPublisher<Any>() {
    override val publishAction: (IDEStartResult, List<PerformanceMetrics.Metric>) -> Unit = { _, _ -> }
  }

  private fun setupDataPaths(testInfo: TestInfo, relativePath: Path): Unit {
    val resourceBasePath = "diff/${relativePath}"

    val logDir = JarUtils.extractResource(resourceBasePath, testDirectory)
    Mockito.lenient().doReturn(logDir).`when`(runContextMock).logsDir
  }

  @Test
  fun metricsDiffCalculation(testInfo: TestInfo) {
    val metricsPublisher: MetricsPublisher<Any> = getMetricsPublisher().apply { addMetricsCollector(getWorkspaceModelMeterCollector()) }

    setupDataPaths(testInfo, Path("before"))
    val metricsBefore: List<PerformanceMetrics.Metric> = metricsPublisher.collectMetrics(runContextMock)

    setupDataPaths(testInfo, Path("after"))
    val metricsAfter: List<PerformanceMetrics.Metric> = metricsPublisher.collectMetrics(runContextMock)

    val diff: List<PerformanceMetrics.Metric> = MetricsDiffCalculator.calculateDiff(metricsBefore, metricsAfter)

    withClue("Generated metrics: ${diff.map { it.id.name to it.value }.joinToString(separator = System.lineSeparator())}") {
      assertSoftly {
        diff.shouldHaveSize(3)

        diff.single { it.id.name == "jps.library.entities.serializer.load.entities.ms" }.value.shouldBe(23)
        diff.single { it.id.name == "jps.module.iml.entities.serializer.load.entities.ms" }.value.shouldBe(702)
        diff.single { it.id.name == "workspaceModel.mutableEntityStorage.replace.by.source.ms" }.value.shouldBe(2260)
      }
    }
  }

  @Test
  fun calculatingNegativeDifference() {
    val metricsBefore = listOf(PerformanceMetrics.newDuration("some_metric", durationMillis = 100))
    val metricsAfter = listOf(PerformanceMetrics.newDuration("some_metric", durationMillis = 50))
    val diff: List<PerformanceMetrics.Metric> = MetricsDiffCalculator.calculateDiff(metricsBefore, metricsAfter, useAbsoluteValue = false)

    diff.single().value.shouldBe(-50)
  }

  private fun generateMetrics(numberOfMetrics: Int): List<PerformanceMetrics.Metric> {
    if (numberOfMetrics <= 0) return listOf()

    return (1..numberOfMetrics)
      .map { index ->
        PerformanceMetrics.newDuration("metric_$index", durationMillis = index)
      }
  }

  @Test
  fun equalCollections() {
    val metricsBefore = generateMetrics(3)
    val metricsAfter = generateMetrics(3)
    val diff: List<PerformanceMetrics.Metric> = MetricsDiffCalculator.calculateDiff(metricsBefore, metricsAfter)

    assertSoftly {
      diff.shouldHaveSize(3)
      diff.shouldForAll { it.value.shouldBe(0) }
    }
  }

  @Test
  fun firstCollectionIsBiggerThenSecond() {
    val metricsBefore = generateMetrics(5)
    val metricsAfter = generateMetrics(4)
    val diff: List<PerformanceMetrics.Metric> = MetricsDiffCalculator.calculateDiff(metricsBefore, metricsAfter)

    assertSoftly {
      diff.shouldHaveSize(5)
      diff.filter { it.value == 0 }.shouldHaveSize(4)
      diff.single { it.value != 0 }.value.shouldBe(5)
    }
  }

  @Test
  fun secondCollectionIsBiggerThenFirst() {
    val metricsBefore = generateMetrics(2)
    val metricsAfter = generateMetrics(6)
    val diff: List<PerformanceMetrics.Metric> = MetricsDiffCalculator.calculateDiff(metricsBefore, metricsAfter)

    assertSoftly {
      diff.shouldHaveSize(6)
      diff.filter { it.value == 0 }.shouldHaveSize(2)
      diff.filter { it.value != 0 }.shouldHaveSize(4)
    }
  }

  @Test
  fun emptyCollections() {
    val diff: List<PerformanceMetrics.Metric> = MetricsDiffCalculator.calculateDiff(listOf(), listOf())
    diff.shouldHaveSize(0)
  }

  @Test
  fun firstCollectionIsEmpty() {
    val metricsBefore = listOf<PerformanceMetrics.Metric>()
    val metricsAfter = generateMetrics(3)
    val diff: List<PerformanceMetrics.Metric> = MetricsDiffCalculator.calculateDiff(metricsBefore, metricsAfter)

    assertSoftly {
      diff.shouldHaveSize(3)
      diff.filter { it.value == 0 }.shouldBeEmpty()
      diff.filter { it.value != 0 }.shouldHaveSize(3)
    }
  }
}