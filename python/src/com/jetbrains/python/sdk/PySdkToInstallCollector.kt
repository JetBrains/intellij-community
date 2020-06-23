// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk

import com.intellij.internal.statistic.eventLog.EventFields
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.project.Project

class PySdkToInstallCollector : CounterUsagesCollector() {

  override fun getGroup(): EventLogGroup = GROUP

  companion object {

    internal enum class DownloadResult { EXCEPTION, SIZE, CHECKSUM, CANCELLED, OK }
    internal enum class InstallationResult { EXCEPTION, EXIT_CODE, TIMEOUT, CANCELLED, OK }
    internal enum class LookupResult { FOUND, NOT_FOUND }

    internal fun logSdkDownload(project: Project?, version: String, result: DownloadResult) {
      downloadEvent.log(project, result, version)
    }

    internal fun logSdkInstallation(project: Project?, version: String, result: InstallationResult) {
      installationEvent.log(project, result, version)
    }

    internal fun logSdkLookup(project: Project?, version: String, result: LookupResult) {
      lookupEvent.log(project, result, version)
    }

    private val GROUP = EventLogGroup("python.sdk.install.events", 1)

    private val downloadEvent = GROUP.registerEvent("install.download",
                                                    EventFields.Enum("download_result", DownloadResult::class.java),
                                                    EventFields.String("py_version"))

    private val installationEvent = GROUP.registerEvent("install.installation",
                                                        EventFields.Enum("installation_result", InstallationResult::class.java),
                                                        EventFields.String("py_version"))

    private val lookupEvent = GROUP.registerEvent("install.lookup",
                                                  EventFields.Enum("lookup_result", LookupResult::class.java),
                                                  EventFields.String("py_version"))
  }
}