// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.statistics

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.annotations.XMap
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write


@Service(Service.Level.PROJECT)
@State(
  name = "PyPackageUsageStatistics",
  storages = [Storage(StoragePathMacros.CACHE_FILE)],
  reportStatistic = false,
  reloadable = false,
)
@ApiStatus.Internal
class PyPackageUsageStatistics : PersistentStateComponent<PyPackageUsageStatistics.LibraryUsageStatisticsState> {
  private val lock = ReentrantReadWriteLock()
  private var statistics = Object2IntOpenHashMap<PackageUsage>()

  override fun getState(): LibraryUsageStatisticsState = lock.read {
    val result = LibraryUsageStatisticsState()
    result.statistics.putAll(statistics)
    result
  }

  override fun loadState(state: LibraryUsageStatisticsState): Unit = lock.write {
    statistics = Object2IntOpenHashMap(state.statistics)
  }

  fun getStatisticsAndResetState(): Map<PackageUsage, Int> = lock.write {
    val old = statistics
    statistics = Object2IntOpenHashMap()
    old
  }

  fun increaseUsages(libraries: Collection<PackageUsage>): Unit = lock.write {
    for (it in libraries) {
      statistics.addTo(it, 1)
    }
  }

  class LibraryUsageStatisticsState {
    @XMap
    @JvmField
    val statistics: HashMap<PackageUsage, Int> = HashMap()
  }

  companion object {
    fun getInstance(project: Project): PyPackageUsageStatistics = project.service()
  }
}

data class PackageUsage(
  var name: String? = null,
  var version: String? = null,
  var interpreterTypeValue: String? = null,
  var targetTypeValue: String? = null,
  var hasSdk: Boolean? = null,
)