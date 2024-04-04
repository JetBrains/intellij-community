// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.installer

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.util.system.CpuArch
import com.intellij.util.system.OS
import com.jetbrains.python.sdk.Product
import com.jetbrains.python.sdk.Release

internal object BinaryInstallerUsagesCollector : CounterUsagesCollector() {

  override fun getGroup(): EventLogGroup = GROUP

  enum class DownloadResult { EXCEPTION, SIZE, CHECKSUM, CANCELLED, OK }
  enum class InstallationResult { EXCEPTION, EXIT_CODE, TIMEOUT, CANCELLED, OK }

  fun logDownloadEvent(release: Release, downloadResult: DownloadResult) {
    downloadEvent.log(DownloadEventFields.getEventPairs(release, downloadResult))
  }

  fun logInstallationEvent(release: Release, installationResult: InstallationResult) {
    installationEvent.log(InstallationEventFields.getEventPairs(release, installationResult))
  }

  internal fun logInstallerException(release: Release, exception: BinaryInstallerException) {
    when (exception) {
      is PrepareException -> {
        when (exception) {
          is WrongSizePrepareException -> DownloadResult.SIZE
          is WrongChecksumPrepareException -> DownloadResult.CHECKSUM
          is CancelledPrepareException -> DownloadResult.CANCELLED
          else -> DownloadResult.EXCEPTION
        }.let { logDownloadEvent(release, it) }
      }
      is ProcessException -> {
        when (exception) {
          is NonZeroExitCodeProcessException -> InstallationResult.EXIT_CODE
          is TimeoutProcessException -> InstallationResult.TIMEOUT
          is CancelledProcessException -> InstallationResult.CANCELLED
          else -> InstallationResult.EXCEPTION
        }.let { logInstallationEvent(release, it) }
      }
    }
  }


  object ContextFields {
    private val product = EventFields.Enum("product", Product::class.java)

    /**
     * regexp: non-empty value consists of digits-dots-dashes in various combinations (like 1, 1.2, 1.2.3-456)
     * Can't use EventFields.Version because of dashes
     * @see com.intellij.internal.statistic.eventLog.events.EventFields.Version
     */
    private val version = EventFields.StringValidatedByInlineRegexp("version", """^[\d\-.]+$""")

    private val os = EventFields.Enum("os", OS::class.java)

    private val cpuArch = EventFields.Enum("cpu_arch", CpuArch::class.java)

    fun getEventPairs(release: Release): List<EventPair<*>> {
      return listOf(
        product.with(release.product),
        version.with(release.version),
        os.with(OS.CURRENT),
        cpuArch.with(CpuArch.CURRENT),
      )
    }

    fun getFields(): MutableList<EventField<*>> {
      return mutableListOf(product, version, os, cpuArch)
    }
  }

  object DownloadEventFields {
    private val downloadResult = EventFields.Enum("download_result", DownloadResult::class.java)
    fun getEventPairs(release: Release, result: DownloadResult): List<EventPair<*>> {
      return ContextFields.getEventPairs(release) + listOf(downloadResult.with(result))
    }

    fun getFields(): List<EventField<*>> {
      return ContextFields.getFields() + listOf(downloadResult)
    }
  }

  object InstallationEventFields {
    private val installationResult = EventFields.Enum("installation_result", InstallationResult::class.java)
    fun getEventPairs(release: Release, result: InstallationResult): List<EventPair<*>> {
      return ContextFields.getEventPairs(release) + listOf(installationResult.with(result))
    }

    fun getFields(): List<EventField<*>> {
      return ContextFields.getFields() + listOf(installationResult)
    }
  }

  private val GROUP = EventLogGroup("python.sdk.installer.events", 1)

  private val downloadEvent = GROUP.registerVarargEvent(
    eventId = "download.finished", fields = DownloadEventFields.getFields().toTypedArray()
  )

  private val installationEvent = GROUP.registerVarargEvent(
    eventId = "installation.finished", fields = InstallationEventFields.getFields().toTypedArray()
  )
}