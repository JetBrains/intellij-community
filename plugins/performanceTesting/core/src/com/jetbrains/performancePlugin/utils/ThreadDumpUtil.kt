// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.utils

import com.intellij.concurrency.ConcurrentCollectionFactory
import com.intellij.diagnostic.ThreadDumper
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import com.intellij.util.text.DateFormatUtil.formatTimeWithSeconds
import kotlinx.coroutines.*
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

@Service
class ThreadDumpService(
  private val coroutineScope: CoroutineScope,
) {
  private val threadDumpingJobs = ConcurrentCollectionFactory.createConcurrentMap<String, Job>()

  @Suppress("unused")
  fun startDumpingThreads(
    activityId: String,
    folderName: String,
    fileNamePrefix: String,
    interval: Duration,
  ) {
    threadDumpingJobs.getOrPut(activityId) {
      val threadDumpsFolder = PathManager.getLogDir().resolve(folderName)
      coroutineScope.launch {
        dumpIdeThreadsPeriodically(threadDumpsFolder, fileNamePrefix, interval)
      }
    }
  }

  fun stopDumpingThreads(
    activityId: String,
  ) {
    val job = threadDumpingJobs.remove(activityId) ?: return
    job.cancel("Stop dumping threads '$activityId'")
  }
}

@OptIn(ExperimentalTime::class)
suspend fun dumpIdeThreadsPeriodically(
  threadDumpsFolder: Path,
  threadDumpFilePrefix: String,
  interval: Duration
) {
  var counter = 0
  while (true) {
    ++counter
    val time = formatTimeWithSeconds(Clock.System.now().toEpochMilliseconds())
    dumpIdeThreads(threadDumpsFolder.resolve("$threadDumpFilePrefix-$time-$counter.txt"))
    delay(interval)
  }
}

suspend fun dumpIdeThreads(threadDumpFile: Path) {
  val threadDump = ThreadDumper.dumpThreadsToString()
  if (!Files.exists(threadDumpFile)) {
    withContext(Dispatchers.IO) {
      Files.createDirectories(threadDumpFile.parent)
      Files.createFile(threadDumpFile)
    }
  }

  withContext(Dispatchers.IO) {
    Files.writeString(threadDumpFile, threadDump)
  }
}
