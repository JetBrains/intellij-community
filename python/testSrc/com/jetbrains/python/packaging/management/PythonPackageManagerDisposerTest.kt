// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.management

import com.intellij.openapi.projectRoots.impl.ProjectJdkImpl
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.sdk.PythonSdkType
import org.junit.jupiter.api.Test

/**
 * A manager must not put itself into the Disposer tree.
 *
 * A manager used to tie every coroutine it starts to itself with `Job.cancelOnDispose(this)`. That call registers
 * the job under the manager, which made the manager a root of the Disposer tree before anything had adopted it.
 * `PythonPackageManagerServiceImpl.forSdk` adopts it right after construction, but that call can fail while the
 * project closes, and a manager left as a root then pinned the project forever. PY-90829 reports the leak.
 *
 * The manager owns a coroutine scope now, so the Disposer sees it only when `forSdk` registers it. This test
 * constructs a manager without `forSdk` and fails if the constructor alone put it into the tree.
 */
@TestApplication
@Subsystems.PackagingRequirements
@Layers.Functional
class PythonPackageManagerDisposerTest {
  private val projectFixture = projectFixture()

  @Test
  fun `a fresh manager is absent from the Disposer tree`() {
    val project = projectFixture.get()
    val sdk = ProjectJdkImpl("PY-90829 test SDK", PythonSdkType.getInstance())

    val manager = TestPythonPackageManager(project, sdk)

    Disposer.getTree().assertNoReferenceKeptInTree(manager)
  }
}
