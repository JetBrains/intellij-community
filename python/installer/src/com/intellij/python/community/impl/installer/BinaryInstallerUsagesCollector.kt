// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.impl.installer

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.project.Project
import com.intellij.util.system.CpuArch
import com.jetbrains.python.sdk.Product
import com.jetbrains.python.sdk.Release
import com.jetbrains.python.sdk.installer.*
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object BinaryInstallerUsagesCollector : CounterUsagesCollector() {

  override fun getGroup(): EventLogGroup = GROUP

  internal enum class DownloadResult { EXCEPTION, SIZE, CHECKSUM, CANCELLED, OK }
  internal enum class InstallationResult { EXCEPTION, EXIT_CODE, TIMEOUT, CANCELLED, OK }
  enum class LookupResult { FOUND, NOT_FOUND }

  internal fun logDownloadEvent(project: Project?, release: Release, downloadResult: DownloadResult) {
    downloadEvent.log(project, DownloadEventFields.getEventPairs(release, downloadResult))
  }

  internal fun logInstallationEvent(project: Project?, release: Release, installationResult: InstallationResult) {
    installationEvent.log(project, InstallationEventFields.getEventPairs(release, installationResult))
  }

  fun logLookupEvent(project: Project?, product: Product, version: String?, lookupResult: LookupResult) {
    lookupEvent.log(project, LookupEventFields.getEventPairs(product, version, lookupResult))
  }


  internal fun logInstallerException(project: Project?, release: Release, exception: BinaryInstallerException) {
    when (exception) {
      is PrepareException -> {
        when (exception) {
          is WrongSizePrepareException -> DownloadResult.SIZE
          is WrongChecksumPrepareException -> DownloadResult.CHECKSUM
          is CancelledPrepareException -> DownloadResult.CANCELLED
          else -> DownloadResult.EXCEPTION
        }.let { logDownloadEvent(project, release, it) }
      }
      is ProcessException -> {
        when (exception) {
          is NonZeroExitCodeProcessException -> InstallationResult.EXIT_CODE
          is TimeoutProcessException -> InstallationResult.TIMEOUT
          is CancelledProcessException -> InstallationResult.CANCELLED
          else -> InstallationResult.EXCEPTION
        }.let { logInstallationEvent(project, release, it) }
      }
    }
  }


  internal object ContextFields {
    private val product = EventFields.Enum("product", Product::class.java)

    /**
     * regexp: non-empty value consists of digits-dots-dashes in various combinations (like 1, 1.2, 1.2.3-456)
     * Can't use EventFields.Version because of dashes
     * @see EventFields.Version
     */
    private val version = EventFields.StringValidatedByInlineRegexp("version", """^[\d\-.]+$""")

    private val cpuArch = EventFields.Enum("cpu_arch", CpuArch::class.java)

    fun getEventPairs(release: Release): List<EventPair<*>> {
      return getEventPairs(release.product, release.version)
    }

    fun getEventPairs(product: Product, version: String?): List<EventPair<*>> {
      return buildList {
        add(this@ContextFields.product.with(product))
        version?.let {
          add(this@ContextFields.version.with(it))
        }
        add(cpuArch.with(CpuArch.CURRENT))
      }
    }

    fun getFields(): MutableList<EventField<*>> {
      return mutableListOf(product, version, cpuArch)
    }
  }

  internal object DownloadEventFields {
    private val downloadResult = EventFields.Enum("download_result", DownloadResult::class.java)
    fun getEventPairs(release: Release, result: DownloadResult): List<EventPair<*>> {
      return ContextFields.getEventPairs(release) + listOf(downloadResult.with(result))
    }

    fun getFields(): List<EventField<*>> {
      return ContextFields.getFields() + listOf(downloadResult)
    }
  }

  internal object InstallationEventFields {
    private val installationResult = EventFields.Enum("installation_result", InstallationResult::class.java)
    fun getEventPairs(release: Release, result: InstallationResult): List<EventPair<*>> {
      return ContextFields.getEventPairs(release) + listOf(installationResult.with(result))
    }

    fun getFields(): List<EventField<*>> {
      return ContextFields.getFields() + listOf(installationResult)
    }
  }

  internal object LookupEventFields {
    private val lookupResult = EventFields.Enum("lookup_result", LookupResult::class.java)
    fun getEventPairs(product: Product, version: String?, result: LookupResult): List<EventPair<*>> {
      return ContextFields.getEventPairs(product, version) + listOf(lookupResult.with(result))
    }

    fun getFields(): List<EventField<*>> {
      return ContextFields.getFields() + listOf(lookupResult)
    }
  }

  private val GROUP = EventLogGroup("python.sdk.installer.events", 2)

  private val downloadEvent = GROUP.registerVarargEvent(
    eventId = "download.finished", fields = DownloadEventFields.getFields().toTypedArray()
  )

  private val installationEvent = GROUP.registerVarargEvent(
    eventId = "installation.finished", fields = InstallationEventFields.getFields().toTypedArray()
  )

  private val lookupEvent = GROUP.registerVarargEvent(
    eventId = "lookup.finished", fields = LookupEventFields.getFields().toTypedArray()
  )
}