// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.common

import com.intellij.openapi.components.service
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.packaging.PyPIPackageRanking
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface PythonPackageManagementListener {
  fun packagesChanged(sdk: Sdk) {}

  @ApiStatus.Internal
  fun outdatedPackagesChanged(sdk: Sdk) {}
}

internal class PythonRankingAwarePackageNameComparator : Comparator<String> {
  val ranking = service<PyPIPackageRanking>().packageRank
  override fun compare(name1: String, name2: String): Int {
    val rank1 = ranking[name1.lowercase()]
    val rank2 = ranking[name2.lowercase()]
    return when {
      rank1 != null && rank2 == null -> -1
      rank1 == null && rank2 != null -> 1
      rank1 != null && rank2 != null && rank1 != rank2 -> rank2 - rank1
      else -> String.CASE_INSENSITIVE_ORDER.compare(name1, name2)
    }
  }
}

