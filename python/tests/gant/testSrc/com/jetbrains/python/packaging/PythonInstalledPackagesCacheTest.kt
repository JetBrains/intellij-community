// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging

import com.intellij.python.sdk.backend.PythonEnvironmentKey
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.deleteExisting
import kotlin.io.path.setLastModifiedTime
import kotlin.time.Duration.Companion.days
import kotlin.time.toJavaDuration

/** The installed packages of an environment stay until its fingerprint changes, or no manager uses them, see [PythonInstalledPackagesCache]. */
@Subsystems.Packaging
@Layers.Functional
internal class PythonInstalledPackagesCacheTest {
  private val environment = PythonEnvironmentKey.Native("local", "/venv/bin/python")
  private val packages = listOf(InstalledPackage("requests", "2.31.0", false), InstalledPackage("mylib", "0.1", true))

  @Test
  fun `an install changes the fingerprint`(@TempDir root: Path) {
    val sitePackages = root.resolve("lib/python3.12/site-packages").createDirectories()
    sitePackages.resolve("requests-2.31.0.dist-info").createDirectories()
    val before = PythonInstalledPackagesCache.fingerprintOf(listOf(root.resolve("lib/python3.12"), sitePackages))

    sitePackages.resolve("idna-3.6.dist-info").createDirectories()

    assertThat(before).isNotNull()
    assertThat(PythonInstalledPackagesCache.fingerprintOf(listOf(sitePackages))).isNotEqualTo(before)
  }

  @Test
  fun `a removal and a changed pth file change the fingerprint`(@TempDir root: Path) {
    val sitePackages = root.resolve("site-packages").createDirectories()
    val distInfo = sitePackages.resolve("requests-2.31.0.dist-info").createDirectories()
    val pth = sitePackages.resolve("mylib.pth").createFile()
    pth.setLastModifiedTime(FileTime.fromMillis(1_000))
    val before = PythonInstalledPackagesCache.fingerprintOf(listOf(sitePackages))

    pth.setLastModifiedTime(FileTime.fromMillis(2_000))
    val afterPth = PythonInstalledPackagesCache.fingerprintOf(listOf(sitePackages))
    distInfo.deleteExisting()
    val afterRemoval = PythonInstalledPackagesCache.fingerprintOf(listOf(sitePackages))

    assertThat(afterPth).isNotEqualTo(before)
    assertThat(afterRemoval).isNotEqualTo(afterPth)
  }

  @Test
  fun `a file that is no package metadata keeps the fingerprint`(@TempDir root: Path) {
    val sitePackages = root.resolve("site-packages").createDirectories()
    val before = PythonInstalledPackagesCache.fingerprintOf(listOf(sitePackages))

    sitePackages.resolve("__pycache__").createDirectories()

    assertThat(PythonInstalledPackagesCache.fingerprintOf(listOf(sitePackages))).isEqualTo(before)
  }

  @Test
  fun `an environment with no site-packages has no fingerprint`(@TempDir root: Path) {
    assertThat(PythonInstalledPackagesCache.fingerprintOf(listOf(root.resolve("lib/python3.12")))).isNull()
    assertThat(PythonInstalledPackagesCache.fingerprintOf(listOf(root.resolve("missing/site-packages")))).isNull()
  }

  @Test
  fun `the packages come back only with the fingerprint of their list`() {
    val cache = PythonInstalledPackagesCache()

    cache.store(environment, LISTER, "a", packages)

    assertThat(cache.packagesOf(environment, LISTER, "a")!!.map { Triple(it.name, it.version, it.editable) })
      .containsExactly(Triple("requests", "2.31.0", false), Triple("mylib", "0.1", true))
    assertThat(cache.packagesOf(environment, LISTER, "b")).isNull()
    assertThat(cache.packagesOf(PythonEnvironmentKey.Native("local", "/other/bin/python"), LISTER, "a")).isNull()
  }

  @Test
  fun `a list of another kind of manager does not come back`() {
    val cache = PythonInstalledPackagesCache()

    cache.store(environment, LISTER, "a", packages)

    assertThat(cache.packagesOf(environment, "other.Manager", "a")).isNull()
  }

  @Test
  fun `the dependency group and the editable location come back`() {
    val cache = PythonInstalledPackagesCache()
    val pkg = InstalledPackage("mylib", "0.1", true, "dev", "file:///src/mylib")

    cache.store(environment, LISTER, "a", listOf(pkg))

    assertThat(cache.packagesOf(environment, LISTER, "a")).containsExactly(pkg)
  }

  @Test
  fun `a dropped environment has no packages`() {
    val cache = PythonInstalledPackagesCache()
    cache.store(environment, LISTER, "a", packages)

    cache.drop(environment, LISTER)

    assertThat(cache.packagesOf(environment, LISTER, "a")).isNull()
  }

  @Test
  fun `an entry that no manager used for the TTL goes`() {
    val cache = PythonInstalledPackagesCache()
    var now = Instant.parse("2026-11-01T00:00:00Z")
    fun setNow(instant: Instant) {
      now = instant
      cache.setClockForTests(Clock.fixed(now, ZoneOffset.UTC))
    }
    setNow(now)
    cache.store(environment, LISTER, "a", packages)
    val used = PythonEnvironmentKey.Native("local", "/used/bin/python")
    cache.store(used, LISTER, "u", packages)

    setNow(now.plus(20.days.toJavaDuration()))
    cache.packagesOf(used, LISTER, "u")
    setNow(now.plus(15.days.toJavaDuration()))
    val state = cache.state

    assertThat(state.environments.map { it.key }).containsExactly("$LISTER:native:local:/used/bin/python")
    assertThat(cache.packagesOf(environment, LISTER, "a")).isNull()
  }

  @Test
  fun `the stored state loads back, without the expired entries`() {
    val cache = PythonInstalledPackagesCache()
    cache.setClockForTests(Clock.fixed(Instant.parse("2026-11-01T00:00:00Z"), ZoneOffset.UTC))
    cache.store(environment, LISTER, "a", packages)
    val loaded = PythonInstalledPackagesCache()
    loaded.setClockForTests(Clock.fixed(Instant.parse("2026-11-02T00:00:00Z"), ZoneOffset.UTC))

    loaded.loadState(cache.state)

    assertThat(loaded.packagesOf(environment, LISTER, "a")!!.map { it.name }).containsExactly("requests", "mylib")

    val late = PythonInstalledPackagesCache()
    late.setClockForTests(Clock.fixed(Instant.parse("2026-12-15T00:00:00Z"), ZoneOffset.UTC))
    late.loadState(cache.state)
    assertThat(late.keysForTests()).isEmpty()
  }

  private companion object {
    const val LISTER = "test.Manager"
  }
}
