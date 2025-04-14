// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.profilers

import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.project.Project
import com.intellij.util.SystemProperties
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

interface Profiler {
  companion object {
    const val PROFILER_PROPERTY = "integrationTests.profiler"

    val EP_NAME: ExtensionPointName<Profiler> = ExtensionPointName("com.jetbrains.performancePlugin.profiler")

    @JvmStatic
    fun isAnyProfilingStarted(): Boolean = EP_NAME.extensionList.any { it.isProfilingStarted }

    @JvmStatic
    fun getCurrentProfilerHandler(): Profiler {
      // this relies on the naming, but we want a determined order and independent plugins/profilers; by default, we choose async
      val all = EP_NAME.extensionList.asSequence().filter { it.isEnabled }.sortedWith(Comparator.comparing { p -> p.javaClass.simpleName })
      return all.firstOrNull() ?: throw RuntimeException("There are no installed profilers")
    }

    @JvmStatic
    fun getCurrentProfilerHandler(project: Project?): Profiler {
      val all = EP_NAME.extensionList.filter { it.isEnabledInProject(project) }
      assert(all.size == 1)
      return all.first()
    }

    @JvmStatic
    fun formatSnapshotName(isMemorySnapshot: Boolean): String {
      val buildNumber = ApplicationInfo.getInstance().build.asString()
      val userName = SystemProperties.getUserName()
      val snapshotDate = SimpleDateFormat("dd.MM.yyyy_HH.mm.ss").format(Date())
      return buildNumber + '_' + (if (isMemorySnapshot) "memory_" else "") + userName + '_' + snapshotDate
    }
  }

  fun startProfiling(activityName: String, options: List<String>)

  suspend fun startProfilingAsync(activityName: String, options: List<String>) {
    blockingContext {
      startProfiling(activityName, options)
    }
  }

  @Throws(Exception::class)
  fun stopProfiling(options: List<String>): String

  fun stopProfileWithNotification(arguments: String): String

  suspend fun stopProfileAsyncWithNotification(arguments: String): String? {
    return blockingContext {
      stopProfileWithNotification(arguments)
    }
  }

  @Throws(IOException::class)
  fun compressResults(pathToResult: String, archiveName: String): File?

  val isEnabled: Boolean

  fun isEnabledInProject(project: Project?): Boolean

  val isProfilingStarted: Boolean
}
