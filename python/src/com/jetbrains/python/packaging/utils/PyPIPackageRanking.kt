// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.utils

import com.google.common.io.Resources
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.jetbrains.python.packaging.PyPackageName
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.annotations.ApiStatus
import java.net.URL

@ApiStatus.Internal
fun PyPIPackageRanking(): PyPIPackageRanking = service<PyPIPackageRankingImpl>()

@ApiStatus.Internal
interface PyPIPackageRanking {
  val rankedPackages: Deferred<Set<PyPackageName>>
}

@Serializable
private data class Packages(
  val packages: List<PackageEntry>
)

@Serializable
private data class PackageEntry(
  val name: String,
  val downloads: Int
)

@Service(Service.Level.APP)
private class PyPIPackageRankingImpl(coroutineScope: CoroutineScope) : PyPIPackageRanking {

  override val rankedPackages: Deferred<HashSet<PyPackageName>> = coroutineScope.async(Dispatchers.IO) {
    loadAndParseRankingData()
  }

  private suspend fun loadAndParseRankingData(): HashSet<PyPackageName> = withContext(Dispatchers.IO) {
    val resource = loadResource()
    parseJsonToList(resource).packages.map { PyPackageName.from(it.name) }.toHashSet()
  }

  private fun loadResource(): URL =
    PyPIPackageRanking::class.java.getResource(RANKING_RESOURCE_PATH)
    ?: error("Python package ranking not found")

  private fun parseJsonToList(resource: URL): Packages {
    val jsonString = Resources.asCharSource(resource, Charsets.UTF_8).read()
    return Json.decodeFromString<Packages>(jsonString)
  }

  private companion object {
    const val RANKING_RESOURCE_PATH = "/packaging/pypi-rank.json"
  }
}