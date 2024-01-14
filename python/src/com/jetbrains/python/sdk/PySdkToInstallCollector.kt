// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.project.Project
import com.jetbrains.python.sdk.installer.*

internal object PySdkToInstallCollector : CounterUsagesCollector() {

  override fun getGroup(): EventLogGroup = GROUP

  internal enum class OS(val os: com.intellij.util.system.OS) {
    WIN(com.intellij.util.system.OS.Windows),
    MAC(com.intellij.util.system.OS.macOS),
    Linux(com.intellij.util.system.OS.Linux),
    FreeBSD(com.intellij.util.system.OS.FreeBSD),
    Other(com.intellij.util.system.OS.Other);

    companion object {
      val CURRENT = entries.first { it.os == com.intellij.util.system.OS.CURRENT }
    }
  }

  enum class DownloadResult { EXCEPTION, SIZE, CHECKSUM, CANCELLED, OK }
  enum class InstallationResult { EXCEPTION, EXIT_CODE, TIMEOUT, CANCELLED, OK }
  enum class LookupResult { FOUND, NOT_FOUND }

  internal fun logSdkLookup(project: Project?, version: String? = null, result: LookupResult) {
    val args = mutableListOf<EventPair<*>>(lookupResultField.with(result), osField.with(OS.CURRENT))
    version?.apply { args.add(versionField.with(this)) }
    lookupEvent.log(project, *args.toTypedArray())
  }

  internal fun logSdkDownload(project: Project?, version: String, result: DownloadResult) {
    downloadEventWin.log(project, result, version)
  }

  internal fun logSdkInstall(project: Project?, version: String? = null, result: InstallationResult) {
    val args = mutableListOf<EventPair<*>>(installationResultField.with(result), osField.with(OS.CURRENT))
    version?.apply { args.add(versionField.with(this)) }
    installationEvent.log(project, *args.toTypedArray())
  }

  internal fun logInstallerException(project: Project?, release: Release, exception: ReleaseInstallerException) {
    when (exception) {
      is PrepareException -> {
        when (exception) {
          is WrongSizePrepareException -> DownloadResult.SIZE
          is WrongChecksumPrepareException -> DownloadResult.CHECKSUM
          is CancelledPrepareException -> DownloadResult.CANCELLED
          else -> DownloadResult.EXCEPTION
        }.apply { logSdkDownload(project, release.version.toString(), this) }
      }
      is ProcessException -> {
        when (exception) {
          is NonZeroExitCodeProcessException -> InstallationResult.EXIT_CODE
          is TimeoutProcessException -> InstallationResult.TIMEOUT
          is CancelledProcessException -> InstallationResult.CANCELLED
          else -> InstallationResult.EXCEPTION
        }.apply { logSdkInstall(project, release.version.toString(), this) }
      }
    }
  }


  private val GROUP = EventLogGroup("python.sdk.install.events", 4)

  private val versionField = EventFields.StringValidatedByRegexpReference("py_version", "version")

  private val osField = EventFields.Enum("os", OS::class.java)

  private val downloadEventWin = GROUP.registerEvent("install.download.win",
                                                     EventFields.Enum("download_result", DownloadResult::class.java),
                                                     versionField)

  private val installationResultField = EventFields.Enum("installation_result", InstallationResult::class.java)

  private val installationEvent = GROUP.registerVarargEvent("install.installation", installationResultField, osField, versionField)

  private val lookupResultField = EventFields.Enum("lookup_result", LookupResult::class.java)

  private val lookupEvent = GROUP.registerVarargEvent("install.lookup", lookupResultField, osField, versionField)
}