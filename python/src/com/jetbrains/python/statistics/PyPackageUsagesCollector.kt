// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.statistics

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.validator.ValidationResultType
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.jetbrains.extensions.getSdk
import com.jetbrains.python.packaging.PyPIPackageCache
import com.jetbrains.python.packaging.PyPackageManager
import com.jetbrains.python.statistics.PyPackageVersionUsagesCollector.Companion.PACKAGE_FIELD
import com.jetbrains.python.statistics.PyPackageVersionUsagesCollector.Companion.PACKAGE_VERSION_FIELD
import com.jetbrains.python.statistics.PyPackageVersionUsagesCollector.Companion.PYTHON_PACKAGE_INSTALLED

/**
 * Reports usages of packages and versions
 */
class PyPackageVersionUsagesCollector : ProjectUsagesCollector() {
  override fun getMetrics(project: Project) = getPackages(project)

  override fun requiresReadAccess(): Boolean = true

  override fun getGroup(): EventLogGroup = GROUP

  companion object {
    private val GROUP = EventLogGroup("python.packages", 3)

    //full list is stored in metadata, see FUS-1218 for more details
    val PACKAGE_FIELD = EventFields.String("package", emptyList())
    val PACKAGE_VERSION_FIELD = EventFields.StringValidatedByRegexp("package_version", "version")
    val PYTHON_PACKAGE_INSTALLED = registerPythonSpecificEvent(GROUP, "python_package_installed", PACKAGE_FIELD, PACKAGE_VERSION_FIELD)
  }
}

private fun getPackages(project: Project): Set<MetricEvent> {
  val result = HashSet<MetricEvent>()
  val pypiPackages = PyPIPackageCache.getInstance()
  for (module in project.modules) {
    val sdk = module.getSdk() ?: continue
    val usageData = getPythonSpecificInfo(sdk)
    PyPackageManager.getInstance(sdk).getRequirements(module).orEmpty()
      .filter { pypiPackages.containsPackage(it.name) }
      .forEach { req ->
        ProgressManager.checkCanceled()
        val version = req.versionSpecs.firstOrNull()?.version?.trim() ?: "unknown"
        val data = ArrayList(usageData) // Not to calculate interpreter on each call
        data.add(PACKAGE_FIELD.with(req.name))
        data.add(PACKAGE_VERSION_FIELD.with(version))
        result.add(PYTHON_PACKAGE_INSTALLED.metric(data))
      }
  }
  return result
}

class PyPackageUsagesValidationRule : CustomValidationRule() {
  override fun getRuleId(): String = "python_packages"

  override fun doValidate(data: String, context: EventContext) =
    if (PyPIPackageCache.getInstance().containsPackage(data)) ValidationResultType.ACCEPTED else ValidationResultType.REJECTED
}