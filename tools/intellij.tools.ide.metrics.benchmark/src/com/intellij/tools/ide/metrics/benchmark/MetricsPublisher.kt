// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("SSBasedInspection")

package com.intellij.tools.ide.metrics.benchmark

import com.intellij.openapi.application.PathManager
import com.intellij.tools.ide.metrics.collector.TelemetryMetricsCollector
import com.intellij.util.concurrency.SynchronizedClearableLazy
import kotlinx.coroutines.runBlocking
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.io.path.writer

interface MetricsPublisher {
  suspend fun publish(uniqueTestIdentifier: String, vararg metricsCollectors: TelemetryMetricsCollector)

  fun publishSync(fullQualifiedTestMethodName: String, vararg metricsCollectors: TelemetryMetricsCollector) {
    runBlocking {
      publish(fullQualifiedTestMethodName, *metricsCollectors)
    }
  }

  companion object {
    fun getInstance(): MetricsPublisher = instance.value

    fun getIdeTestLogFile(): Path = PathManager.getSystemDir().resolve("testlog").resolve("idea.log")
    fun truncateTestLog(): Unit = run { getIdeTestLogFile().writer(options = arrayOf(StandardOpenOption.TRUNCATE_EXISTING)).write("") }
  }
}

private val instance: SynchronizedClearableLazy<MetricsPublisher> = SynchronizedClearableLazy {
  IJPerfMetricsPublisherImpl()
}