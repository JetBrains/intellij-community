// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.statistics

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
import com.intellij.openapi.project.Project
import com.jetbrains.python.getOrNull
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.sdk.legacy.PythonSdkUtil

/**
 * Reports usages of packages and versions
 */
internal class PyPackageVersionUsagesCollector : ProjectUsagesCollector() {
  override suspend fun collect(project: Project): Set<MetricEvent> = buildSet {
    for (sdk in project.sdks.filter(PythonSdkUtil::isPythonSdk)) {
      val manager = PythonPackageManager.forSdk(project, sdk)
      addAll(manager.getDeclaredPackages())
      addAll(manager.getInstalledPackages())
    }
  }

  override fun getGroup(): EventLogGroup = GROUP

  private val GROUP = EventLogGroup("python.packages", 9)

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


  private suspend fun PythonPackageManager.getDeclaredPackages(): Set<MetricEvent> {
    val dependencies = extractDependenciesCached()?.getOrNull() ?: return emptySet()
    val usageData = getPythonSpecificInfo(sdk)
    return dependencies.mapTo(HashSet()) { dep ->
      PYTHON_PACKAGE_INSTALLED.metric(usageData + listOf(
        PACKAGE_FIELD.with(dep.name),
        PACKAGE_VERSION_FIELD.with(dep.version),
      ))
    }
  }

  private suspend fun PythonPackageManager.getInstalledPackages(): Set<MetricEvent> {
    return listInstalledPackages().mapTo(HashSet()) { pkg ->
      PYTHON_PACKAGE_INSTALLED_IN_SDK.metric(
        PACKAGE_FIELD.with(pkg.name),
        PACKAGE_VERSION_FIELD.with(pkg.version),
        EXECUTION_TYPE.with(sdk.executionType.value),
        INTERPRETER_TYPE.with(sdk.interpreterType.value),
      )
    }
  }
}

val PACKAGE_FIELD = EventFields.StringValidatedByDictionary("package", "python_packages.ndjson")
val PACKAGE_VERSION_FIELD = EventFields.StringValidatedByRegexpReference("package_version", "version")
