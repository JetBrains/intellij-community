// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.interpreter

import com.intellij.python.junit5Tests.framework.pyProjectFixture
import com.intellij.python.pyproject.model.evolution.EvoPyProjectModel
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.pathInProjectFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.jetbrains.python.configuration.interpreter.workspaceModulesOf
import kotlinx.coroutines.delay
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.io.path.Path
import kotlin.time.Duration.Companion.milliseconds

/** The Python > Workspace page lists the modules of the Python projects only, not every module of the project. */
@TestApplication
internal class PyWorkspaceModulesTest {
  private val projectFixture = projectFixture()
  private val pyProjectFixture = projectFixture.pyProjectFixture(projectFixture.pathInProjectFixture(Path("app")))
  private val plainModuleFixture = projectFixture.moduleFixture("plain")

  @Test
  fun `a module that is not a Python project is not listed`(): Unit = timeoutRunBlocking {
    val project = projectFixture.get()
    val pyProject = pyProjectFixture.get()
    plainModuleFixture.get()
    // The snapshot of the Python projects follows the modules asynchronously.
    val model = EvoPyProjectModel.getInstance(project)
    while (model.snapshotOrNull()?.forPyProject(pyProject) == null) delay(50.milliseconds)

    assertThat(workspaceModulesOf(project)).containsExactly(pyProject.residesOnModule)
  }
}
