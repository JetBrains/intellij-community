// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.statistics

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.eventLog.validator.ValidationResultType
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomWhiteListRule
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.reference.SoftReference
import com.intellij.util.concurrency.NonUrgentExecutor
import com.jetbrains.extensions.getSdk
import com.jetbrains.python.packaging.PyPIPackageCache
import com.jetbrains.python.packaging.PyPackageManager
import org.jetbrains.concurrency.CancellablePromise
import java.lang.ref.WeakReference
import java.util.concurrent.Callable

/**
 * Reports usages of packages and versions
 */
class PyPackageVersionUsagesCollector : ProjectUsagesCollector() {
  override fun getMetrics(project: Project, indicator: ProgressIndicator?) = getPackages(project, indicator)

  override fun getGroupId() = "python.packages"

  override fun getVersion() = 2
}

private fun getPackages(project: Project, indicator: ProgressIndicator?): CancellablePromise<out Set<MetricEvent>> {
  return ReadAction.nonBlocking(Callable {
    val result = HashSet<MetricEvent>()
    val app = ApplicationManager.getApplication()
    for (module in project.modules) {
      val sdk = module.getSdk() ?: continue
      val usageData = FeatureUsageData().addPythonSpecificInfo(sdk)
      app.runReadAction {
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
    }
    result
  }).withProgressIndicator(indicator).expireWith(project).submit(NonUrgentExecutor.getInstance())
}

private fun getPyPiPackagesCache() = PyPIPackageCache.getInstance().packageNames.map(String::toLowerCase).toSet()


class PyPackageUsagesWhiteListRule : CustomWhiteListRule() {
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