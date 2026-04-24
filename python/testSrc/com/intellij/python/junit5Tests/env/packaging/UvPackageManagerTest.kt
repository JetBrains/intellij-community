// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.env.packaging

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.python.junit5Tests.framework.env.PyEnvTestCase
import com.intellij.python.junit5Tests.framework.env.pySdkFixture
import com.intellij.python.test.env.junit5.pyUvVenvFixture
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.jetbrains.python.getOrThrow
import com.jetbrains.python.packaging.common.PythonPackageManagementListener
import com.jetbrains.python.packaging.management.PythonPackageManager
import kotlinx.coroutines.CompletableDeferred
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.minutes

@PyEnvTestCase
class UvPackageManagerTest {
  private val tempPathFixture = tempPathFixture()
  private val projectFixture = projectFixture()
  private val moduleFixture = projectFixture.moduleFixture(tempPathFixture, addPathToSourceRoot = true)
  private val sdkFixture = pySdkFixture().pyUvVenvFixture(addToSdkTable = true, moduleFixture = moduleFixture)

  @Test
  fun outdatedPackageWithExtrasTest(): Unit = timeoutRunBlocking(1.minutes) {
    tempPathFixture.get().resolve("pyproject.toml").writeText("""
      [project]
      name = "repro"
      version = "0.1.0"
      requires-python = ">=3.13"
      dependencies = [
          "httpx[http2,brotli]~=0.24.0"
      ]
    """.trimIndent())

    val manager = PythonPackageManager.forSdk(projectFixture.get(), sdkFixture.get())

    val httpxOutdated = CompletableDeferred<Unit>()
    val connection = ApplicationManager.getApplication().messageBus.connect()
    connection.subscribe(PythonPackageManager.PACKAGE_MANAGEMENT_TOPIC, object : PythonPackageManagementListener {
      override fun outdatedPackagesChanged(sdk: Sdk) {
        if (manager.listOutdatedPackagesSnapshot().values.any { it.name == "httpx" }) {
          httpxOutdated.complete(Unit)
        }
      }
    })

    try {
      manager.sync().getOrThrow()

      val installed = manager.listInstalledPackages()
      val httpxPkg = installed.find { it.name == "httpx" }
      assertThat(httpxPkg).describedAs("httpx should be installed").isNotNull()
      assertThat(httpxPkg!!.version).startsWith("0.24.")

      httpxOutdated.await()
      val outdated = manager.listOutdatedPackagesSnapshot()
      val pkg = outdated.values.find { it.name == "httpx" }
      assertThat(pkg).describedAs("httpx should be outdated").isNotNull()
      assertThat(pkg!!.name).isEqualTo("httpx")
      assertThat(pkg.version).isEqualTo(httpxPkg.version)
    }
    finally {
      connection.disconnect()
    }
  }
}
