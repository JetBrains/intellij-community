// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.statistics

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.eventLog.validator.ValidationResultType
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.reference.SoftReference
import com.jetbrains.extensions.getSdk
import com.jetbrains.python.packaging.PyPIPackageCache
import com.jetbrains.python.packaging.PyPackageManager
import java.lang.ref.WeakReference

/**
 * Reports usages of packages and versions
 */
class PyPackageVersionUsagesCollector : ProjectUsagesCollector() {
  override fun getMetrics(project: Project) = getPackages(project)

  override fun requiresReadAccess(): Boolean = true

  override fun getGroupId() = "python.packages"

  override fun getVersion() = 2
}

private fun getPackages(project: Project): Set<MetricEvent> {
  val result = HashSet<MetricEvent>()
  for (module in project.modules) {
    val sdk = module.getSdk() ?: continue
    val usageData = FeatureUsageData().addPythonSpecificInfo(sdk)
    PyPackageManager.getInstance(sdk).getRequirements(module)?.apply {
      val packageNames = getPyPiPackagesCache()
      filter { it.name.toLowerCase() in packageNames }.forEach { req ->
        ProgressManager.checkCanceled()
        val version = req.versionSpecs.firstOrNull()?.version?.trim() ?: "unknown"
        result.add(MetricEvent("python_package_installed",
                               usageData.copy() // Not to calculate interpreter on each call
                                 .addData("package", req.name)
                                 .addData("package_version", version)))
      }
    }
  }
  return result
}

private fun getPyPiPackagesCache() = PyPIPackageCache.getInstance().packageNames.map(String::toLowerCase).toSet()


class PyPackageUsagesValidationRule : CustomValidationRule() {
  private var packagesRef: WeakReference<Set<String>>? = null
  @Synchronized
  private fun getPackages(): Set<String> {
    SoftReference.dereference(packagesRef)?.let { return it }
    val pyPiPackages = getPyPiPackagesCache()
    packagesRef = WeakReference(pyPiPackages)
    return pyPiPackages
  }

  override fun acceptRuleId(ruleId: String?) = "python_packages" == ruleId

  override fun doValidate(data: String, context: EventContext) =
    if (data.toLowerCase() in getPackages()) ValidationResultType.ACCEPTED else ValidationResultType.REJECTED
}