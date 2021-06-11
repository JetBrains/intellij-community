// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.packaging

import com.google.common.io.Resources
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

object PyPIPackageRanking {

  val packageRank = mutableMapOf<String, Int>()

  fun reload() {
    packageRank.clear()
    val gson = Gson()
    val resource = PyPIPackageRanking::class.java.getResource("/packaging/pypi-ranking.json") ?: error("Python package ranking not found")
    val array = Resources.asCharSource(resource, Charsets.UTF_8).openBufferedStream().use {
      gson.fromJson(it, Array<PyPackageRankingEntry>::class.java)
    }
    array.forEach {
      packageRank[it.name.toLowerCase()] = it.downloads
    }
  }

  class PyPackageRankingEntry(@SerializedName("project") val name: String, @SerializedName("download_count") val downloads: Int)
}