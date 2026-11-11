// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.env.packaging

import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.python.junit5Tests.framework.env.PyEnvTestCase
import com.intellij.python.junit5Tests.framework.env.RequiresUv
import com.intellij.python.junit5Tests.framework.env.pyEnvironmentFixture
import com.intellij.python.junit5Tests.framework.pyProjectInProjectFixture
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.test.env.common.PredefinedPyEnvironments
import com.intellij.python.test.env.junit5.pyUvVenvFixture
import com.intellij.testFramework.common.timeoutRunBlocking
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.getOrThrow
import com.jetbrains.python.junit5.framework.pyExternalSystemProjectFixture
import com.jetbrains.python.packaging.common.PythonPackageManagementListener
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.sdk.uv.UvMode
import com.jetbrains.python.testDataPath
import kotlinx.coroutines.CompletableDeferred
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.time.Duration.Companion.minutes

/**
 * The outdated packages of a uv workspace. The root accepts Python 3.8 and the member `core` accepts Python 3.9, so uv
 * resolves for Python 3.9. The environment runs Python 3.12. For Python 3.9, urllib3 2.6.3 is the newest release.
 */
@PyEnvTestCase
@Subsystems.Interpreters
@Layers.Functional
@RequiresUv
class UvWorkspaceOutdatedPackagesTest {
  private val projectFixture = pyExternalSystemProjectFixture(Path.of(testDataPath) / "junit5/uv/outdatedWorkspace")
  private val pyProjectFixture = projectFixture.pyProjectInProjectFixture("workspace-root")
  private val interpreterFixture = pyEnvironmentFixture(PredefinedPyEnvironments.VENV_3_12)
    .pyUvVenvFixture(pyProjectFixture = pyProjectFixture, mode = UvMode.Project)

  @Test
  fun `the latest version supports the lowest Python that every member accepts`(): Unit = timeoutRunBlocking(3.minutes) {
    val manager = syncedManager()

    assertThat(manager.listOutdatedPackagesSnapshot().values.associate { it.name to it.latestVersion })
      .containsExactlyInAnyOrderEntriesOf(mapOf("idna" to "3.20", "urllib3" to "2.6.3"))
  }

  @Test
  fun `a change of requires-python in a member checks the outdated packages again`(): Unit = timeoutRunBlocking(3.minutes) {
    val manager = syncedManager()
    manager.listDeclaredPackagesCached()

    val outdatedChanged = CompletableDeferred<Unit>()
    val connection = projectFixture.get().messageBus.connect()
    connection.subscribe(PythonPackageManager.PACKAGE_MANAGEMENT_TOPIC, object : PythonPackageManagementListener {
      override fun outdatedPackagesChanged(interpreter: PythonInterpreter) {
        outdatedChanged.complete(Unit)
      }
    })
    try {
      val memberToml = checkNotNull(VfsUtil.findFile(pyProjectFixture.get().baseDir.resolve("packages/core/pyproject.toml"), true))
      edtWriteAction {
        VfsUtil.saveText(memberToml, VfsUtil.loadText(memberToml).replace(">=3.9", ">=3.10"))
      }
      outdatedChanged.await()
    }
    finally {
      connection.disconnect()
    }

    assertThat(manager.listOutdatedPackagesSnapshot().values.associate { it.name to it.latestVersion })
      .containsEntry("urllib3", "2.8.0")
  }

  /** Installs the locked packages of the workspace, and waits for the check of the outdated packages that this starts. */
  private suspend fun syncedManager(): PythonPackageManager {
    val manager = PythonPackageManager.forPythonInterpreter(projectFixture.get(), interpreterFixture.get())
    manager.syncLocked().getOrThrow()
    manager.awaitOutdatedPackages()
    return manager
  }
}