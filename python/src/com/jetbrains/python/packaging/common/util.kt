// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.common

import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.packaging.PyPIPackageRanking
import com.jetbrains.python.packaging.conda.CondaPackageManager
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.pip.PipPythonPackageManager
import com.jetbrains.python.sdk.PythonSdkUtil
import org.jetbrains.annotations.ApiStatus

object PackageManagerHolder {
  private val cache = mutableMapOf<String, PythonPackageManager>()

  fun forSdk(project: Project, sdk: Sdk): PythonPackageManager? {
    if (sdk.homePath in cache) return cache[sdk.homePath] // todo[akniazev] replace with sdk key

    val manager = when {
      PythonSdkUtil.isConda(sdk) -> CondaPackageManager(project, sdk) // todo[akniazev] extract to an extension point
      else -> PipPythonPackageManager(project, sdk)
    }
    cache[sdk.homePath!!] = manager

    return manager
  }
}

@ApiStatus.Experimental
interface PythonPackageManagementListener {
  fun packagesChanged(sdk: Sdk)
}

internal val RANKING_AWARE_PACKAGE_NAME_COMPARATOR: java.util.Comparator<String> = Comparator { name1, name2 ->
  val ranking = PyPIPackageRanking.packageRank
  val rank1 = ranking[name1.lowercase()]
  val rank2 = ranking[name2.lowercase()]
  return@Comparator when {
    rank1 != null && rank2 == null -> -1
    rank1 == null && rank2 != null -> 1
    rank1 != null && rank2 != null -> rank2 - rank1
    else -> String.CASE_INSENSITIVE_ORDER.compare(name1, name2)
  }
}