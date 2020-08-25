// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.stats.completion.network.status.bean

import com.intellij.internal.statistic.eventLog.EventLogConfiguration
import com.intellij.lang.Language
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Version

data class AnalyticsPlatformSettings(val productCode: String? = null, val versions: List<EndpointSettings> = emptyList())

data class EndpointSettings(val releaseType: ReleaseType = ReleaseType.ALL,
                            val majorBuildVersionBorders: MajorVersionBorders = MajorVersionBorders(null, null),
                            val completionLanguage: Language = Language.ANY,
                            val fromBucket: Int = 0,
                            val toBucket: Int = 255,
                            val endpoint: String? = null) {
  fun satisfies(): Boolean {
    val applicationInfo = ApplicationInfo.getInstance()
    val currentReleaseType = if (ApplicationManager.getApplication().isEAP) ReleaseType.EAP else ReleaseType.RELEASE
    if (releaseType != ReleaseType.ALL && releaseType != currentReleaseType) return false
    val version = Version.parseVersion(applicationInfo.fullVersion)
    if (version == null || !majorBuildVersionBorders.satisfies(version)) return false
    val bucket = EventLogConfiguration.bucket
    if (bucket < fromBucket || bucket > toBucket) return false
    return true
  }
}

enum class ReleaseType {
  EAP, RELEASE, ALL
}

data class MajorVersionBorders(val majorVersionFrom: Version?, val majorVersionTo: Version?) {
  fun satisfies(majorVersion: Version): Boolean {
    return (majorVersionFrom == null || majorVersion >= majorVersionFrom) &&
           (majorVersionTo == null || majorVersion <= majorVersionTo)
  }
}