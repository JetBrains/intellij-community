package com.intellij.ide.starter.junit5

import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.runner.CurrentTestMethod
import com.intellij.ide.starter.runner.Starter
import com.intellij.tools.ide.metrics.collector.metrics.MetricsSelectionStrategy
import com.intellij.tools.ide.metrics.collector.starter.metricsPublisherDI
import com.intellij.tools.ide.metrics.collector.starter.publishing.MetricsPublisher
import com.intellij.tools.ide.metrics.collector.starter.publishing.addMeterCollector
import com.intellij.tools.ide.metrics.collector.starter.publishing.addSpanCollector
import com.intellij.tools.ide.metrics.collector.telemetry.SpanFilter
import com.intellij.tools.ide.performanceTesting.commands.CommandChain
import com.intellij.tools.ide.performanceTesting.commands.exitApp
import examples.data.IdeaCommunityCases
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldNotBeEmpty
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.kodein.di.DI
import kotlin.time.Duration.Companion.minutes

fun initDI() {
  di = DI {
    extend(di)
    importAll(metricsPublisherDI)

    // You also may register your own metrics publisher
    //bindProvider<MetricsPublisher<*>>(overrides = true) {
    //  object : MetricsPublisher<Any>() {
    //    override val publishAction: (IDEStartResult, List<PerformanceMetrics.Metric>) -> Unit
    //      get() = TODO("Not yet implemented")
    //  }
    //}
  }
}

class MetricsCollectionTest {
  companion object {
    // You can make that also as a JUnit5 TestListener that will enable it for all tests
    @JvmStatic
    @BeforeAll
    fun beforeAll(): Unit {
      initDI()
    }
  }

  @Test
  fun testCollectionMetrics() {
    val metricPrefixes = listOf("jps.", "workspaceModel.", "FilePageCache.")
    val spanNames = listOf("project.opening")

    val context = Starter.newContext(CurrentTestMethod.get()!!.displayName, IdeaCommunityCases.GradleJitPackSimple)
      .prepareProjectCleanImport()

    val exitCommandChain = CommandChain().exitApp()
    val startResult = context.runIDE(commands = exitCommandChain, runTimeout = 4.minutes)

    val collectedMetrics = MetricsPublisher.newInstance
      // add span collector (from opentelemetry.json file that is located in the log directory)
      .addSpanCollector(SpanFilter.nameInList(spanNames))
      // add meters collector (from .csv files that is located in log directory)
      .addMeterCollector(MetricsSelectionStrategy.SUM) {
        metricPrefixes.any { prefix -> it.name.startsWith(prefix) }
      }
      .publish(startResult)
      .collectMetrics(startResult)

    withClue("Collected metrics should not be empty") {
      collectedMetrics.shouldNotBeEmpty()
    }
  }
}