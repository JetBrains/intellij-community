// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.service
import com.intellij.python.sdk.backend.PythonEnvironmentKey
import com.intellij.util.io.DigestUtil
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.Tag
import com.intellij.util.xmlb.annotations.XCollection
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.getLastModifiedTime
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

/**
 * A package that a list of an environment gave, as [PythonInstalledPackagesCache] keeps it. [dependencyGroup] is the name of
 * the dependency group that declares it, and [editableLocation] is the URI of the sources of an editable install.
 */
@ApiStatus.Internal
data class InstalledPackage(
  val name: String,
  val version: String,
  val editable: Boolean,
  val dependencyGroup: String? = null,
  val editableLocation: String? = null,
)

/**
 * The installed packages of each local Python environment from the last list, with a fingerprint of the environment on
 * the disk, see [fingerprintOf]. A manager takes them at its init when the fingerprint is the same, so the start of a
 * project lists no environment that did not change. An entry that no manager used for [TTL] goes.
 *
 * Each entry also names its lister: the kind of manager that made the list, such as pip or uv. Two kinds of manager can
 * list one environment with other data, so a manager takes only the list of its own kind.
 *
 * It is a cache: an entry with another fingerprint is ignored, and a missing entry costs one list.
 */
@Service(Service.Level.APP)
@State(name = "PythonInstalledPackagesCache", storages = [Storage(StoragePathMacros.CACHE_FILE)])
@ApiStatus.Internal
class PythonInstalledPackagesCache : PersistentStateComponent<PythonInstalledPackagesCache.State> {
  /** The clock of the uses, see [TTL]. A test replaces it, see [setClockForTests]. */
  @Volatile
  private var clock: Clock = Clock.systemUTC()

  class State {
    @XCollection(style = XCollection.Style.v2)
    var environments: MutableList<StoredEnvironment> = mutableListOf()
  }

  @Tag("environment")
  class StoredEnvironment {
    @Attribute("key")
    var key: String = ""

    @Attribute("fingerprint")
    var fingerprint: String = ""

    @Attribute("used")
    var usedMillis: Long = 0

    @XCollection(style = XCollection.Style.v2)
    var packages: MutableList<StoredPackage> = mutableListOf()
  }

  @Tag("package")
  class StoredPackage {
    @Attribute("name")
    var name: String = ""

    @Attribute("version")
    var version: String = ""

    @Attribute("editable")
    var editable: Boolean = false

    @Attribute("group")
    var dependencyGroup: String? = null

    @Attribute("location")
    var editableLocation: String? = null
  }

  private val entries = ConcurrentHashMap<String, StoredEnvironment>()

  override fun getState(): State {
    dropExpired()
    return State().also { it.environments = entries.values.sortedBy { entry -> entry.key }.toMutableList() }
  }

  override fun loadState(state: State) {
    entries.clear()
    state.environments.forEach { entries[it.key] = it }
    dropExpired()
  }

  /**
   * The packages of [environment] from the last list of [lister], when [fingerprint] is the one of that list, or `null`.
   * A hit counts as a use, see [TTL].
   */
  fun packagesOf(environment: PythonEnvironmentKey, lister: String, fingerprint: String): List<InstalledPackage>? {
    val entry = entries[keyOf(environment, lister)]?.takeIf { it.fingerprint == fingerprint } ?: return null
    entry.usedMillis = clock.millis()
    return entry.packages.map { InstalledPackage(it.name, it.version, it.editable, it.dependencyGroup, it.editableLocation) }
  }

  /** Keeps [packages] as the packages that [lister] listed in [environment] with [fingerprint], see [fingerprintOf]. */
  fun store(environment: PythonEnvironmentKey, lister: String, fingerprint: String, packages: List<InstalledPackage>) {
    val entry = StoredEnvironment().also { entry ->
      entry.key = keyOf(environment, lister)
      entry.fingerprint = fingerprint
      entry.usedMillis = clock.millis()
      entry.packages = packages.mapTo(mutableListOf()) { pkg ->
        StoredPackage().also {
          it.name = pkg.name
          it.version = pkg.version
          it.editable = pkg.editable
          it.dependencyGroup = pkg.dependencyGroup
          it.editableLocation = pkg.editableLocation
        }
      }
    }
    entries[entry.key] = entry
  }

  /** Drops the packages that [lister] listed in [environment], such as for a list that this cache cannot keep. */
  fun drop(environment: PythonEnvironmentKey, lister: String) {
    entries.remove(keyOf(environment, lister))
  }

  private fun dropExpired() {
    val oldest = clock.millis() - TTL.inWholeMilliseconds
    entries.values.removeIf { it.usedMillis < oldest }
  }

  /** For tests: makes [clock] the clock of the uses, see [TTL]. */
  @TestOnly
  fun setClockForTests(clock: Clock) {
    this.clock = clock
  }

  /** For tests: the keys of the entries this cache holds. */
  @TestOnly
  fun keysForTests(): Set<String> = entries.keys.toSet()

  companion object {
    /** How long an entry that no manager uses stays. */
    val TTL: Duration = 30.days

    @JvmStatic
    fun getInstance(): PythonInstalledPackagesCache = service()

    /**
     * The fingerprint of the environment that [sysPath] states: for each `site-packages` or `dist-packages` directory,
     * its path, the sorted names of its `*.dist-info` and `*.egg-info` entries, and the modification time of each
     * `.pth` file. An install or a removal with pip, uv or conda changes it. `null` when [sysPath] names no such
     * directory, or one cannot be read: such an environment uses no entry.
     */
    fun fingerprintOf(sysPath: List<Path>): String? {
      val directories = sysPath.filter { it.name == "site-packages" || it.name == "dist-packages" }.distinct()
      if (directories.isEmpty()) return null
      return buildString {
        for (directory in directories) {
          if (!directory.isDirectory()) return null
          append(directory).append('\n')
          val children = try {
            Files.list(directory).use { stream -> stream.toList() }
          }
          catch (_: IOException) {
            return null
          }
          for (child in children.sortedBy { it.name }) {
            val name = child.name
            when {
              name.endsWith(".dist-info") || name.endsWith(".egg-info") -> append(name).append('\n')
              name.endsWith(".pth") -> {
                val modified = try {
                  child.getLastModifiedTime().toMillis()
                }
                catch (_: IOException) {
                  return null
                }
                append(name).append('@').append(modified).append('\n')
              }
            }
          }
        }
      }.let { DigestUtil.sha256Hex(it.toByteArray()) }
    }

    private fun keyOf(environment: PythonEnvironmentKey, lister: String): String = when (environment) {
      is PythonEnvironmentKey.Native -> "$lister:native:${environment.machine}:${environment.path}"
      is PythonEnvironmentKey.Target -> "$lister:target:${environment.targetId}:${environment.path}"
    }
  }
}
