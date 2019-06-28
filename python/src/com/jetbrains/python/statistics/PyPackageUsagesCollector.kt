// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.statistics

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.jetbrains.extensions.getSdk
import com.jetbrains.python.packaging.PyPIPackageCache
import com.jetbrains.python.packaging.PyPackageManager

/**
 * Reports usages of packages and versions
 */
class PyPackageVersionUsagesCollector : ProjectUsagesCollector() {
  override fun getMetrics(project: Project) = getPackages(project)

  override fun getGroupId() = "python.packages"

  override fun getVersion() = 2
}

private fun getPackages(project: Project): Set<MetricEvent> {
  val result = HashSet<MetricEvent>()
  val app = ApplicationManager.getApplication()
  for (module in project.modules) {
    val sdk = module.getSdk() ?: continue
    val usageData = FeatureUsageData().addPythonSpecificInfo(sdk)
    app.runReadAction {
      PyPackageManager.getInstance(sdk).getRequirements(module)?.apply {
        val packageNames = PyPIPackageCache.getInstance().packageNames
        filter { it.name in packageNames }.forEach { req ->
          val version = req.versionSpecs.firstOrNull()?.version?.trim() ?: "unknown"
          result.add(MetricEvent("python_package_installed",
                                 usageData.copy() // Not to calculate interpreter on each call
                                   .addData("package", req.name)
                                   .addData("package_version", version)))
        }
      }
    }
  }
  return result
}
