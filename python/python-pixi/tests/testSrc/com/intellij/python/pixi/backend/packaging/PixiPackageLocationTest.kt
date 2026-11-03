// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pixi.backend.packaging

import com.intellij.idea.TestFor
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.getOrThrow
import com.jetbrains.python.isSuccess
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.net.URI
import kotlin.io.path.pathString
import kotlin.io.path.createDirectories
import kotlin.io.path.readText
import kotlin.io.path.writeText

@TestApplication
@Subsystems.PackagingRequirements
@Layers.Functional
@TestFor(issues = ["PY-91735"])
internal class PixiPackageLocationTest {
  private val rootFixture = tempPathFixture()

  @Test
  fun `a target directory uses its workspace relative path`(): Unit = timeoutRunBlocking {
    val root = rootFixture.get()
    val source = root.resolve("source with spaces").createDirectories()
    source.resolve("pyproject.toml").writeText("[project]\nname = 'source-probe'\nversion = '1.0'\n")

    val location = resolvePixiPackageLocation(source.toUri(), root).getOrThrow().relativeToWorkspace(root).getOrThrow()

    assertThat(location.arguments).containsExactly("--path", "./source with spaces", "--", "source-probe")
  }

  @Test
  fun `a target legacy directory keeps its named relative requirement`(): Unit = timeoutRunBlocking {
    val root = rootFixture.get()
    val source = root.resolve("legacy source").createDirectories()
    source.resolve("setup.py").writeText("from setuptools import setup\nsetup(name='legacy-probe', version='1.0')\n")

    val location = resolvePixiPackageLocation(source.toUri(), root) { PyResult.success("legacy-probe") }
      .getOrThrow().relativeToWorkspace(root).getOrThrow()

    assertThat(location.arguments).containsExactly("--", "legacy-probe @ ./legacy source")
    assertThat(location.isPathDependency).isTrue()
  }

  @Test
  fun `a target wheel uses a portable relative requirement`(): Unit = timeoutRunBlocking {
    val root = rootFixture.get()
    val wheel = root.resolve("source with spaces").createDirectories().resolve("source_probe-1.0-py3-none-any.whl")

    val location = resolvePixiPackageLocation(wheel.toUri(), root).getOrThrow().relativeToWorkspace(root).getOrThrow()

    assertThat(location.arguments).containsExactly("--", "source_probe @ ./source with spaces/source_probe-1.0-py3-none-any.whl")
  }

  @Test
  fun `an external target source cannot persist an unmapped host path`(): Unit = timeoutRunBlocking {
    val root = rootFixture.get()
    val workspace = root.resolve("workspace").createDirectories()
    val wheel = root.resolve("source_probe-1.0-py3-none-any.whl")

    val result = resolvePixiPackageLocation(wheel.toUri(), root).getOrThrow().relativeToWorkspace(workspace)

    assertThat(result.isSuccess).isFalse()
  }

  @Test
  fun `a source metadata probe supplies a legacy directory name`(): Unit = timeoutRunBlocking {
    val root = rootFixture.get()
    root.resolve("setup.py").writeText("from setuptools import setup\nsetup(name='legacy-name', version='1.0')\n")

    val result = resolvePixiPackageLocation(root.toUri(), root) { source ->
      assertThat(source).isEqualTo(root.toUri())
      PyResult.success("legacy-name")
    }

    assertThat(result.isSuccess).isTrue()
    assertThat(result.getOrThrow().name).isEqualTo("legacy-name")
    assertThat(result.getOrThrow().arguments).containsExactly("--", "legacy-name @ ${root.toUri().toASCIIString()}")
    assertThat(result.getOrThrow().isPathDependency).isTrue()
    assertThat(root.resolve("pyproject.toml")).doesNotExist()
  }

  @Test
  fun `an unresolved URL uses source metadata instead of the filename`(): Unit = timeoutRunBlocking {
    val location = URI("git+https://example.org/repo.git@revision")

    val result = resolvePixiPackageLocation(location, rootFixture.get()) { source ->
      assertThat(source).isEqualTo(location)
      PyResult.success("actual-distribution")
    }

    assertThat(result.isSuccess).isTrue()
    assertThat(result.getOrThrow().arguments).containsExactly("--", "actual-distribution @ $location")
  }

  @ParameterizedTest
  @ValueSource(strings = ["", "--unsafe", "name @ https://example.org"])
  fun `source metadata cannot introduce an invalid requirement name`(name: String): Unit = timeoutRunBlocking {
    val root = rootFixture.get()

    val result = resolvePixiPackageLocation(root.toUri(), root) { PyResult.success(name) }

    assertThat(result.isSuccess).isFalse()
  }

  @Test
  fun `a directory install uses explicit project metadata without changing it`(): Unit = timeoutRunBlocking {
    val root = rootFixture.get()
    val manifest = root.resolve("pyproject.toml")
    val text = "# Keep this comment\n[project]\nname = 'Different.Package_Name'\nversion = '1.0'\n"
    manifest.writeText(text)
    val result = resolvePixiPackageLocation(root.toUri(), root).getOrThrow()
    assertThat(result.name).isEqualTo("Different.Package_Name")
    assertThat(result.arguments).containsExactly("--path", root.pathString, "--", "Different.Package_Name")
    assertThat(result.isPathDependency).isTrue()
    assertThat(manifest.readText()).isEqualTo(text)
  }

  @Test
  fun `a wheel install preserves its distribution name`(): Unit = timeoutRunBlocking {
    val root = rootFixture.get()
    val result = resolvePixiPackageLocation(root.resolve("my_package-1.0-py3-none-any.whl").toUri(), root).getOrThrow()
    assertThat(result.name).isEqualTo("my_package")
    assertThat(result.arguments).containsExactly("--", "my_package @ ${root.resolve("my_package-1.0-py3-none-any.whl").toUri()}")
    assertThat(result.isPathDependency).isFalse()
  }

  @Test
  fun `a named VCS install keeps the requested revision`(): Unit = timeoutRunBlocking {
    val location = URI("git+https://example.org/repo.git@revision#egg=my-package")
    val result = resolvePixiPackageLocation(location, rootFixture.get()).getOrThrow()
    assertThat(result.arguments).containsExactly("--", "my-package @ $location")
  }

  @Test
  fun `a remote wheel uses its standard distribution name`(): Unit = timeoutRunBlocking {
    val location = URI("https://example.org/files/my_package-1.0-py3-none-any.whl?download=1#sha256=abc")

    val result = resolvePixiPackageLocation(location, rootFixture.get())

    assertThat(result.isSuccess).isTrue()
    assertThat(result.getOrThrow().name).isEqualTo("my_package")
    assertThat(result.getOrThrow().arguments).containsExactly("--", "my_package @ $location")
  }

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun `source archive metadata overrides a plausible filename`(remote: Boolean): Unit = timeoutRunBlocking {
    val root = rootFixture.get()
    val location = if (remote) URI("https://example.org/files/wrong-name-1.0.tar.gz")
    else root.resolve("wrong-name-1.0.tar.gz").toUri()

    val result = resolvePixiPackageLocation(location, root) { source ->
      assertThat(source).isEqualTo(location)
      PyResult.success("actual-name")
    }

    assertThat(result.isSuccess).isTrue()
    assertThat(result.getOrThrow().name).isEqualTo("actual-name")
    assertThat(result.getOrThrow().arguments).containsExactly("--", "actual-name @ $location")
    assertThat(result.getOrThrow().isPathDependency).isFalse()
  }

  @ParameterizedTest
  @ValueSource(strings = ["my_package-1.0.tar.gz", "my-package-1.2rc1.zip"])
  fun `source archives retain their distribution name`(fileName: String): Unit = timeoutRunBlocking {
    val root = rootFixture.get()
    val name = fileName.substringBeforeLast('-')
    for (location in listOf(root.resolve(fileName).toUri(), URI("https://example.org/files/$fileName"))) {
      val result = resolvePixiPackageLocation(location, root)
      assertThat(result.isSuccess).isTrue()
      assertThat(result.getOrThrow().name).isEqualTo(name)
      assertThat(result.getOrThrow().arguments).containsExactly("--", "$name @ $location")
      assertThat(result.getOrThrow().isPathDependency).isFalse()
    }
  }

  @ParameterizedTest
  @ValueSource(strings = ["git+https://example.org/repo.git", "https://example.org/package.tar.gz", "https://host/pkg#egg=--unsafe"])
  fun `an unresolved location fails without guessing the package name`(location: String): Unit = timeoutRunBlocking {
    assertThat(resolvePixiPackageLocation(URI(location), rootFixture.get()).isSuccess).isFalse()
  }

  @Test
  fun `a virtual project has no inferred distribution name`(): Unit = timeoutRunBlocking {
    val root = rootFixture.get()
    root.resolve("pyproject.toml").writeText("[tool.pixi.workspace]\nname = 'not-a-distribution'")
    assertThat(resolvePixiPackageLocation(root.toUri(), root).isSuccess).isFalse()
  }
}
