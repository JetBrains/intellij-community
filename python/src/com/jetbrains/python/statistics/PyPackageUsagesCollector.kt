// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.statistics

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.jetbrains.extensions.getSdk
import com.jetbrains.python.packaging.PyPIPackageCache
import com.jetbrains.python.packaging.PyPackageManager
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.sdk.PythonSdkAdditionalData

/**
 * Reports usages of packages and versions
 */
internal class PyPackageVersionUsagesCollector : ProjectUsagesCollector() {
  override fun getMetrics(project: Project) = getPackages(project) + getInstalledPackages(project)

  override fun requiresReadAccess(): Boolean = true

  override fun getGroup(): EventLogGroup = GROUP

  private val GROUP = EventLogGroup("python.packages", 5)

  //full list is stored in metadata, see FUS-1218 for more details
  private val PYTHON_PACKAGE_INSTALLED = registerPythonSpecificEvent(GROUP,
                                                                     "python_package_installed",
                                                                     PACKAGE_FIELD,
                                                                     PACKAGE_VERSION_FIELD)

  private val PYTHON_PACKAGE_INSTALLED_IN_SDK = GROUP.registerVarargEvent("python_packages_installed_in_sdk",
                                                                          EXECUTION_TYPE,
                                                                          INTERPRETER_TYPE,
                                                                          PACKAGE_FIELD,
                                                                          PACKAGE_VERSION_FIELD)


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

  private fun getInstalledPackages(project: Project): Set<MetricEvent> {
    val result = HashSet<MetricEvent>()
    val pypiPackages = PyPIPackageCache.getInstance()
    for (module in project.modules) {
      val sdk = module.getSdk() ?: continue
      if (sdk.sdkAdditionalData !is PythonSdkAdditionalData) continue
      val executionType = sdk.executionType
      val interpreterType = sdk.interpreterType
      PythonPackageManager.forSdk(project, sdk).installedPackages
        .filter { pypiPackages.containsPackage(it.name) }
        .forEach { pythonPackage ->
          val version = pythonPackage.version
          val data = buildList {
            add(PACKAGE_FIELD.with(pythonPackage.name))
            add(PACKAGE_VERSION_FIELD.with(version))
            add(EXECUTION_TYPE.with(executionType.value))
            add(INTERPRETER_TYPE.with(interpreterType.value))
          }
          result.add(PYTHON_PACKAGE_INSTALLED_IN_SDK.metric(data))
        }
    }
    return result
  }
}

val PACKAGE_FIELD = EventFields.StringValidatedByEnum("package", "python_packages")
val PACKAGE_VERSION_FIELD = EventFields.StringValidatedByRegexp("package_version", "version")
