// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.interpreter

import com.intellij.openapi.project.Project
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.pathInProjectFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.jetbrains.python.configuration.interpreter.workspaceAddTargetOf
import com.jetbrains.python.project.asPyProjectCandidate
import com.intellij.testFramework.common.timeoutRunBlocking
import com.jetbrains.python.configuration.interpreter.workspaceModulesOf
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.createDirectories

/**
 * Without a Python project, the Python > Workspace page lists the module that the user opened, as in GoLand, WebStorm,
 * DataGrip and RustRover: the module of the project directory, not an attached one. Its pane adds the first interpreter.
 */
@TestApplication
internal class PyWorkspaceAddTargetTest {
  private val projectFixture = projectFixture()
  private val primaryFixture = projectFixture.moduleFixture(projectFixture.directoryInProjectFixture(Path("")), addPathToSourceRoot = true)
  private val attachedFixture = projectFixture.moduleFixture(projectFixture.directoryInProjectFixture(Path("attached")), addPathToSourceRoot = true)

  @Test
  fun `the first interpreter goes to the module of the project directory`() {
    val primary = primaryFixture.get()
    attachedFixture.get()
    assertThat(workspaceAddTargetOf(projectFixture.get())).isEqualTo(primary)
    assertThat(workspaceModulesOf(projectFixture.get())).containsExactly(primary)
  }
}

/**
 * The root module that is not a Python project yet gets the regular pane of the page: it is a Python project candidate,
 * and that writes nothing to the module.
 */
@TestApplication
internal class PyProjectCandidateTest {
  private val projectFixture = projectFixture()
  private val moduleFixture = projectFixture.moduleFixture(projectFixture.directoryInProjectFixture(Path("app")), addPathToSourceRoot = true)

  @Test
  fun `a module is a candidate at its content root`(): Unit = timeoutRunBlocking {
    val module = moduleFixture.get()
    val candidate = checkNotNull(module.asPyProjectCandidate())
    assertThat(candidate.residesOnModule).isEqualTo(module)
    assertThat(candidate.baseDir).isEqualTo(projectFixture.get().basePath?.let { Path(it).resolve("app") })
  }
}

/** A project with one module, which is not at the project directory, adds the first interpreter to that module. */
@TestApplication
internal class PyWorkspaceSingleModuleAddTargetTest {
  private val projectFixture = projectFixture()
  private val moduleFixture = projectFixture.moduleFixture(projectFixture.directoryInProjectFixture(Path("app")), addPathToSourceRoot = true)

  @Test
  fun `the first interpreter goes to the only module`() {
    assertThat(workspaceAddTargetOf(projectFixture.get())).isEqualTo(moduleFixture.get())
  }
}

/** The directory [path] of the project, created on disk, so a module can have it as its content root. */
private fun TestFixture<Project>.directoryInProjectFixture(path: Path): TestFixture<Path> = testFixture {
  val directory = pathInProjectFixture(path).init().normalize()
  withContext(Dispatchers.IO) { directory.createDirectories() }
  initialized(directory) {}
}
