package com.intellij.usage.statistics.toolwindow

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.registry.Registry
import com.intellij.usage.statistics.UsageStatisticsBundle
import org.apache.http.client.utils.URIBuilder
import org.jetbrains.annotations.Nls

abstract class OpenAnalyticsPlatformReportAction(@Nls text: String) : AnAction(text) {
  companion object {
    fun openReport(groupId: String, eventId: String? = null, fieldValue: String? = null) {
      val applicationInfo = ApplicationInfo.getInstance()
      val build = applicationInfo.build
      val versionType = when {
        ApplicationManager.getApplication().isEAP || build.isSnapshot -> "eap"
        applicationInfo.fullVersion.contains("RC") -> "rc"
        else -> "release"
      }

      val productVersion = buildString {
        append("${applicationInfo.majorVersion}.${applicationInfo.minorVersion}")
        if (applicationInfo.microVersion != null) {
          append(".${applicationInfo.microVersion}")
        }
      }

      val url = URIBuilder(Registry.stringValue("usage.statistics.analytics.platform.url"))
        .setPath("/tool-window/statistics")
        .addParameter("product", build.productCode)
        .addParameter("group", groupId)
        .addParameter("productVersionTypes", versionType)
        .addParameter("productVersion", productVersion)
        .addParameter("event", eventId)
        .addParameter("filter", fieldValue)
        .build()
      BrowserUtil.browse(url)
    }
  }
}

class OpenReportByGroupAction(private val groupId: String)
  : OpenAnalyticsPlatformReportAction(UsageStatisticsBundle.message("open.report.for.group.0", groupId)) {
  override fun actionPerformed(e: AnActionEvent) {
    openReport(groupId)
  }
}

class OpenReportByEventAction(private val groupId: String, private val eventId: String)
  : OpenAnalyticsPlatformReportAction(UsageStatisticsBundle.message("open.report.for.event.0", eventId)) {
  override fun actionPerformed(e: AnActionEvent) {
    openReport(groupId, eventId)
  }
}

class OpenEventReportWithFilterAction(private val groupId: String, private val eventId: String, private val filter: String)
  : OpenAnalyticsPlatformReportAction(UsageStatisticsBundle.message("open.report.for.event.0.filtered.by.1", eventId, filter)) {
  override fun actionPerformed(e: AnActionEvent) {
    openReport(groupId, eventId, filter)
  }
}