// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.stats.network.status.bean

import com.intellij.internal.statistic.eventLog.EventLogConfiguration
import com.intellij.lang.Language
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager

data class AnalyticsPlatformSettings(val productCode: String, val versions: List<EndpointSettings>)

data class EndpointSettings(val releaseType: ReleaseType,
                            val majorBuildVersionBorders: MajorVersionBorders,
                            val completionLanguage: Language,
                            val fromBucket: Int,
                            val toBucket: Int,
                            val endpoint: String) {
  fun satisfies(): Boolean {
    val applicationInfo = ApplicationInfo.getInstance()
    val currentReleaseType = if (ApplicationManager.getApplication().isEAP) ReleaseType.EAP else ReleaseType.RELEASE
    if (releaseType != ReleaseType.ALL && releaseType != currentReleaseType) return false
    if (!majorBuildVersionBorders.satisfies(MajorVersion(applicationInfo.majorVersion))) return false
    val bucket = EventLogConfiguration.bucket
    if (bucket < fromBucket || bucket > toBucket) return false
    return true
  }
}

enum class ReleaseType {
  EAP, RELEASE, ALL
}

data class MajorVersionBorders(val majorVersionFrom: MajorVersion?, val majorVersionTo: MajorVersion?) {
  fun satisfies(majorVersion: MajorVersion): Boolean {
    return (majorVersionFrom == null || majorVersion >= majorVersionFrom) &&
           (majorVersionTo == null || majorVersion <= majorVersionTo)
  }
}

data class MajorVersion(val version: String) : Comparable<MajorVersion> {
  private val versionParts: List<Int?> = version.split(".").map { it.toIntOrNull() }

  override fun compareTo(other: MajorVersion): Int {
    val commonPartsCount = minOf(versionParts.size, other.versionParts.size)
    for (i in 0 until commonPartsCount) {
      if (versionParts[i] == other.versionParts[i]) continue
      if (versionParts[i] == null) return -1
      if (other.versionParts[i] == null) return 1
      return versionParts[i]!! - other.versionParts[i]!!
    }
    if (versionParts.size == other.versionParts.size) return 0
    return versionParts.size - other.versionParts.size
  }
}