// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.statistics

import com.intellij.internal.statistic.beans.UsageDescriptor
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.jetbrains.python.packaging.PyPIPackageCache
import com.jetbrains.python.packaging.PyPackageManager
import com.jetbrains.python.sdk.PythonSdkType

/**
 * Reports usages like Django.2.0.6 or Flask.null (if latest)
 */
object PyPackageVersionUsagesCollector : ProjectUsagesCollector() {
  override fun getUsages(project: Project) = getUsagesInt(project, true)

  override fun getGroupId() = "statistics.python.packages.versions"
}


object PyPackageUsagesCollector : ProjectUsagesCollector() {
  override fun getUsages(project: Project) = getUsagesInt(project, false)

  override fun getGroupId() = "statistics.python.packages"
}

private fun getUsagesInt(project: Project, addVersion: Boolean): Set<UsageDescriptor> {
  val result = HashSet<UsageDescriptor>()
  val app = ApplicationManager.getApplication()
  for (module in ModuleManager.getInstance(project).modules) {
    val sdk = PythonSdkType.findPythonSdk(module) ?: continue
    app.runReadAction {
      PyPackageManager.getInstance(sdk).getRequirements(module)?.apply {
        val packageNames = PyPIPackageCache.getInstance().packageNames
        filter { it.name in packageNames }.forEach { req ->
          val value = req.name + if (addVersion) "." + req.versionSpecs.firstOrNull()?.version?.trim() else ""
          result.add(UsageDescriptor(value, 1))
        }
      }
    }
  }
  return result
}
