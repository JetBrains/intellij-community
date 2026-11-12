// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.env.pixi

import com.intellij.idea.TestFor
import com.intellij.platform.eel.provider.localEel
import com.intellij.python.junit5Tests.framework.LeakedProcessReporterExtension
import com.intellij.python.pixi.backend.PixiPyTool
import com.intellij.python.pixi.backend.PyPixiBackendBundle
import com.intellij.python.pixi.backend.project.PixiPyProjectManager
import com.intellij.python.pixi.backend.workspace.PixiWorkspace
import com.intellij.python.pixi.backend.workspace.findPixiWorkspace
import com.intellij.python.pixi.test.env.PixiPyEnvironment
import com.intellij.python.pixi.test.env.pixiEnvironment
import com.intellij.python.pytools.backend.getCustomExecutablePath
import com.intellij.python.pytools.backend.setCustomExecutablePath
import com.intellij.python.test.env.core.PyEnvironmentFactory
import com.intellij.python.test.env.junit5.PythonFactoryExtension
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.getOrThrow
import com.jetbrains.python.isFailure
import com.jetbrains.python.isSuccess
import org.apache.tuweni.toml.Toml
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.minutes

@TestApplication
@Subsystems.Interpreters
@Layers.Functional
@TestFor(issues = ["PY-91711"])
@ExtendWith(PythonFactoryExtension::class, LeakedProcessReporterExtension::class)
internal class PixiPyProjectManagerTest {
  @TempDir
  lateinit var temporaryDirectory: Path

  @ParameterizedTest
  @CsvSource(
    "child-project, child-project", "child project, child-project", "Child_Project, child-project", "-child, child",
    "NULL, existing-project", nullValues = ["NULL"],
  )
  fun `creates a discoverable Pixi project in a path with spaces`(
    name: String?, expectedName: String, factory: PyEnvironmentFactory,
  ): Unit = timeoutRunBlocking(5.minutes) {
    val where = temporaryDirectory.resolve("parent with spaces").resolve("existing-project").createDirectories()
    val projectRoot = name?.let { where.resolve(it) } ?: where
    withExecutable(factory.pixiExecutable()) {
      val result = PixiPyProjectManager().createProject(where, name)

      assertThat(result.isSuccess).isTrue()
      result.getOrThrow()
      val manifest = projectRoot.resolve("pyproject.toml")
      assertThat(manifest).exists()
      val parsed = Toml.parse(manifest)
      assertThat(parsed.errors()).isEmpty()
      assertThat(parsed.getString("project.name")).isEqualTo(expectedName)
      assertThat(parsed.getTable("tool.pixi.workspace")).isNotNull()
      assertThat(findPixiWorkspace(projectRoot)).isEqualTo(PixiWorkspace(projectRoot, listOf("default")))
      assertThat(projectRoot.resolve("pixi.toml")).doesNotExist()
    }
  }

  @Test
  fun `conversion preserves existing source files`(factory: PyEnvironmentFactory): Unit = timeoutRunBlocking(5.minutes) {
    val where = temporaryDirectory.resolve("existing-project").createDirectories()
    val source = where.resolve("main.py")
    val content = "# Keep the source file.\nprint('existing project')\n"
    source.writeText(content)
    withExecutable(factory.pixiExecutable()) {
      val result = PixiPyProjectManager().createProject(where, null)

      assertThat(result.isSuccess).isTrue()
      result.getOrThrow()
      assertThat(source.readText()).isEqualTo(content)
      assertThat(where.resolve("pyproject.toml")).exists()
    }
  }

  @ParameterizedTest
  @CsvSource("pixi.toml, Core Package, core-package", "pyproject.toml, Core Package, core-package", "pixi.toml, -child, child")
  @TestFor(issues = ["PY-91735"])
  fun `a new package joins the enclosing workspace`(
    manifestName: String, directoryName: String, packageName: String, factory: PyEnvironmentFactory,
  ): Unit =
    timeoutRunBlocking(5.minutes) {
      val root = temporaryDirectory.resolve("workspace with spaces").createDirectories()
      val prefix = if (manifestName == "pyproject.toml") "tool.pixi." else ""
      val manifest = root.resolve(manifestName)
      val original = """
        # Keep this workspace comment.
        [${prefix}workspace]
        channels = ['conda-forge']
        platforms = ['linux-64', 'linux-aarch64', 'osx-arm64', 'osx-64', 'win-64']

        [${prefix}dependencies]
        python = '3.13.*' # Keep this constraint.

      """.trimIndent()
      manifest.writeText(original)
      val packages = root.resolve("packages").createDirectories()
      withExecutable(factory.pixiExecutable()) {
        val result = PixiPyProjectManager().createProject(packages, directoryName)

        assertThat(result.isSuccess).isTrue()
        result.getOrThrow()
        val child = packages.resolve(directoryName)
        val table = Toml.parse(manifest)
        assertThat(table.errors()).isEmpty()
        val dependency = table.getTable("${prefix}pypi-dependencies.$packageName")
        assertThat(dependency).isNotNull()
        assertThat(dependency?.getString("path")).isEqualTo("packages/$directoryName")
        assertThat(dependency?.getBoolean("editable")).isTrue()
        assertThat(manifest.readText()).startsWith(original)
        val childTable = Toml.parse(child.resolve("pyproject.toml"))
        assertThat(childTable.errors()).isEmpty()
        assertThat(childTable.getString("project.name")).isEqualTo(packageName)
        assertThat(childTable.getTable("tool.pixi")).isNull()
        assertThat(childTable.getString("build-system.build-backend")).isNotBlank()
        assertThat(child.resolve("src/${packageName.replace('-', '_')}/__init__.py")).exists()
        assertThat(findPixiWorkspace(child)).isEqualTo(PixiWorkspace(root, listOf("default")))
        assertThat(child.resolve("pixi.toml")).doesNotExist()

        val registered = manifest.readText()
        val childOriginal = child.resolve("pyproject.toml").readText()
        val repeated = PixiPyProjectManager().createProject(packages, directoryName)
        assertThat(repeated.isSuccess).isTrue()
        repeated.getOrThrow()
        assertThat(manifest.readText()).isEqualTo(registered)
        assertThat(child.resolve("pyproject.toml").readText()).isEqualTo(childOriginal)
      }
    }

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  @TestFor(issues = ["PY-91735"])
  fun `registering an existing package preserves its files`(hasManifest: Boolean, factory: PyEnvironmentFactory): Unit =
    timeoutRunBlocking(5.minutes) {
      val root = temporaryDirectory.resolve("workspace").createDirectories()
      val manifest = root.resolve("pixi.toml")
      manifest.writeText(
        "[workspace]\nchannels = ['conda-forge']\n" +
        "platforms = ['linux-64', 'linux-aarch64', 'osx-arm64', 'osx-64', 'win-64']\n" +
        "[dependencies]\npython = '3.13.*'\n"
      )
      val child = root.resolve("existing").createDirectories()
      val source = child.resolve("src/existing").createDirectories().resolve("__init__.py")
      val sourceOriginal = "VALUE = 42\n"
      source.writeText(sourceOriginal)
      val childManifest = child.resolve("pyproject.toml")
      val original = """
        # Keep the package comment.
        [project]
        name = 'published-name'
        version = '1.2.3'

        [build-system]
        requires = ['hatchling']
        build-backend = 'hatchling.build'

        [tool.hatch.build.targets.wheel]
        packages = ['src/existing']
      """.trimIndent()
      if (hasManifest) childManifest.writeText(original)
      withExecutable(factory.pixiExecutable()) {
        val result = PixiPyProjectManager().createProject(child, null)

        assertThat(result.isSuccess).isTrue()
        result.getOrThrow()
        val dependencyName = if (hasManifest) "published-name" else "existing"
        val dependencies = Toml.parse(manifest).getTable("pypi-dependencies")
        assertThat(dependencies?.keySet()).containsExactly(dependencyName)
        assertThat(dependencies?.getTable(dependencyName)?.getString("path")).isEqualTo("existing")
        assertThat(source.readText()).isEqualTo(sourceOriginal)
        if (hasManifest) assertThat(childManifest.readText()).isEqualTo(original)
        assertThat(findPixiWorkspace(child)).isEqualTo(PixiWorkspace(root, listOf("default")))
      }
    }

  @Test
  fun `initializing an existing Pixi project preserves its manifest`(factory: PyEnvironmentFactory): Unit = timeoutRunBlocking(5.minutes) {
    val where = temporaryDirectory.resolve("existing-project").createDirectories()
    val manifest = where.resolve("pyproject.toml")
    val content = """
      # Keep this project comment.
      [project]
      name = "existing-project"
      version = "1.0.0"

      [tool.pixi.workspace]
      channels = ["conda-forge"]
      platforms = []
    """.trimIndent()
    manifest.writeText(content)
    withExecutable(factory.pixiExecutable()) {
      val result = PixiPyProjectManager().createProject(where, null)

      assertThat(result.isSuccess).isTrue()
      result.getOrThrow()
      assertThat(manifest.readText()).isEqualTo(content)
    }
  }

  @Test
  fun `a destination file causes a failure without data loss`(factory: PyEnvironmentFactory): Unit = timeoutRunBlocking(5.minutes) {
    val destination = temporaryDirectory.resolve("child-project")
    destination.writeText("Keep this file.\n")
    withExecutable(factory.pixiExecutable()) {
      val result = PixiPyProjectManager().createProject(temporaryDirectory, "child-project")

      assertThat(result.isFailure).isTrue()
      assertThat(destination.readText()).isEqualTo("Keep this file.\n")
      assertThat(temporaryDirectory.resolve("pyproject.toml")).doesNotExist()
    }
  }

  @ParameterizedTest
  @ValueSource(strings = ["child-project", "child project"])
  fun `a missing Pixi executable returns an error without creating a project`(name: String): Unit = timeoutRunBlocking {
    withExecutable(temporaryDirectory.resolve("missing-pixi")) {
      val result = PixiPyProjectManager().createProject(temporaryDirectory, name)

      assertThat(result.isFailure).isTrue()
      assertThat(temporaryDirectory.resolve(name)).doesNotExist()
    }
  }

  @Test
  fun `conversion normalizes the package name without renaming its directory`(factory: PyEnvironmentFactory): Unit =
    timeoutRunBlocking(5.minutes) {
      val where = temporaryDirectory.resolve("existing project").createDirectories()
      withExecutable(factory.pixiExecutable()) {
        val result = PixiPyProjectManager().createProject(where, null)

        assertThat(result.isSuccess).isTrue()
        result.getOrThrow()
        val parsed = Toml.parse(where.resolve("pyproject.toml"))
        assertThat(parsed.errors()).isEmpty()
        assertThat(parsed.getString("project.name")).isEqualTo("existing-project")
        assertThat(temporaryDirectory.resolve("existing-project")).doesNotExist()
      }
    }

  @Test
  fun `a project name without ASCII letters or digits fails before creating files`(factory: PyEnvironmentFactory): Unit =
    timeoutRunBlocking(5.minutes) {
      withExecutable(factory.pixiExecutable()) {
        val result = PixiPyProjectManager().createProject(temporaryDirectory, "!!!")

        assertThat(result.isFailure).isTrue()
        assertThat(result.errorOrNull?.message).isEqualTo(PyPixiBackendBundle.message("python.pixi.project.name.invalid"))
        assertThat(temporaryDirectory.resolve("!!!")).doesNotExist()
        assertThat(temporaryDirectory.resolve("pyproject.toml")).doesNotExist()
      }
    }

  private suspend fun withExecutable(executable: Path, action: suspend () -> Unit) {
    val tool = PixiPyTool.getInstance()
    val descriptor = localEel.descriptor
    val previous = tool.getCustomExecutablePath(descriptor)
    tool.setCustomExecutablePath(descriptor, executable)
    try {
      action()
    }
    finally {
      tool.setCustomExecutablePath(descriptor, previous)
    }
  }

  private suspend fun PyEnvironmentFactory.pixiExecutable(): Path =
    checkNotNull(createEnvironment(environmentSpec).unwrap<PixiPyEnvironment>()).pixiExecutable

  companion object {
    private val environmentSpec = pixiEnvironment(PIXI_TEST_VERSION) {
      pythonVersion = pythonVersion("3.13")
      libraries { +"requests==2.32.3" }
    }
  }
}
