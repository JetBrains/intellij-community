// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.project.Project

class PySdkToInstallCollector : CounterUsagesCollector() {

  override fun getGroup(): EventLogGroup = GROUP

  companion object {

    internal enum class OS { WIN, MAC }
    internal enum class DownloadResult { EXCEPTION, SIZE, CHECKSUM, CANCELLED, OK }
    internal enum class InstallationResult { EXCEPTION, EXIT_CODE, TIMEOUT, CANCELLED, OK }
    internal enum class LookupResult { FOUND, NOT_FOUND }

    internal fun logSdkDownloadOnWindows(project: Project?, version: String, result: DownloadResult) {
      downloadEventWin.log(project, result, version)
    }

    internal fun logSdkInstallationOnWindows(project: Project?, version: String, result: InstallationResult) {
      installationEvent.log(project, installationResultField.with(result), osField.with(OS.WIN), versionField.with(version))
    }

    internal fun logSdkLookupOnWindows(project: Project?, version: String, result: LookupResult) {
      lookupEvent.log(project, lookupResultField.with(result), osField.with(OS.WIN), versionField.with(version))
    }

    internal fun logSdkInstallationOnMac(project: Project?, result: InstallationResult) {
      installationEvent.log(project, installationResultField.with(result), osField.with(OS.MAC))
    }

    internal fun logSdkLookupOnMac(project: Project?, result: LookupResult) {
      lookupEvent.log(project, lookupResultField.with(result), osField.with(OS.MAC))
    }

    private val GROUP = EventLogGroup("python.sdk.install.events", 3)

    private val versionField = EventFields.StringValidatedByRegexp("py_version", "version")

    private val osField = EventFields.Enum("os", OS::class.java)

    private val downloadEventWin = GROUP.registerEvent("install.download.win",
                                                       EventFields.Enum("download_result", DownloadResult::class.java),
                                                       versionField)

    private val installationResultField = EventFields.Enum("installation_result", InstallationResult::class.java)

    private val installationEvent = GROUP.registerVarargEvent("install.installation", installationResultField, osField, versionField)

    private val lookupResultField = EventFields.Enum("lookup_result", LookupResult::class.java)

    private val lookupEvent = GROUP.registerVarargEvent("install.lookup", lookupResultField, osField, versionField)
  }
}