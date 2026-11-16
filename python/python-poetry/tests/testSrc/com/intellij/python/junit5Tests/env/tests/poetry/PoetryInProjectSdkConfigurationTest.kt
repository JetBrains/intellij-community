// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.env.tests.poetry

import com.intellij.idea.TestFor
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.python.community.execService.EnvVar
import com.intellij.python.junit5Tests.framework.env.PyEnvTestCase
import com.intellij.python.junit5Tests.framework.env.RequiresPoetry
import com.intellij.python.junit5Tests.framework.pyProjectFixture
import com.intellij.python.pyproject.PY_PROJECT_TOML
import com.intellij.python.sdk.backend.getSdkAPI
import com.intellij.python.sdk.backend.removeTestSdkFromOpenProjects
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.jetbrains.python.getOrThrow
import com.jetbrains.python.poetry.sdk.configuration.PyPoetrySdkConfiguration
import com.jetbrains.python.sdk.add.v2.EelOrJustPath.Companion.asEelOrJustPath
import com.jetbrains.python.sdk.add.v2.toFileSystem
import com.jetbrains.python.sdk.configuration.CreateInterpreterInfo
import com.jetbrains.python.sdk.configuration.CreateInterpreterInfoWithInterpreterCreator
import com.jetbrains.python.sdk.configuration.getInterpreterCreator
import com.jetbrains.python.sdk.poetry.configurePoetryEnvironment
import com.jetbrains.python.sdk.poetry.poetryCacheEnvRoots
import com.jetbrains.python.sdk.poetry.runPoetry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.InstanceOfAssertFactories.type
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.minutes

/**
 * The poetry configurator puts the environment where `virtualenvs.in-project` says. A project with `in-project = true` in
 * its `poetry.toml` gets an SDK on the in-project `.venv`, and poetry creates no environment in its cache. Without the
 * setting, a new environment goes to the cache of poetry.
 */
@TestFor(classes = [PyPoetrySdkConfiguration::class], issues = ["PY-90389"])
@PyEnvTestCase
@RequiresPoetry
class PoetryInProjectSdkConfigurationTest {
  private val modulePathFixture = tempPathFixture(prefix = "poetry-in-project")
  private val pyProject by projectFixture().pyProjectFixture(modulePathFixture)

  @Test
  fun `an existing in-project venv gets the sdk and no cache env is created`(): Unit = timeoutRunBlocking(5.minutes) {
    val baseDir = createPoetryProject(inProject = true)
    runPoetry(baseDir.asEelOrJustPath(), "install", "--no-root").getOrThrow()
    assertThat(baseDir.resolve(".venv")).isDirectory()

    val info = assertThat(PyPoetrySdkConfiguration().checkEnvironmentAndPrepareSdkCreator(pyProject, emptyList()))
      .asInstanceOf(type(CreateInterpreterInfo.ExistingEnv::class.java))
      .actual()

    withCreatedSdk(baseDir, info) { sdkHome -> assertInProjectAndNoCacheEnv(baseDir, sdkHome) }
  }

  @Test
  fun `a new env is created in the project when poetry toml asks for it`(): Unit = timeoutRunBlocking(5.minutes) {
    val baseDir = createPoetryProject(inProject = true)
    lockWithoutEnv(baseDir)

    val info = assertThat(PyPoetrySdkConfiguration().checkEnvironmentAndPrepareSdkCreator(pyProject, emptyList()))
      .asInstanceOf(type(CreateInterpreterInfo.WillCreateEnv::class.java))
      .actual()

    withCreatedSdk(baseDir, info) { sdkHome -> assertInProjectAndNoCacheEnv(baseDir, sdkHome) }
  }

  @Test
  fun `a new env is created in the poetry cache when no config asks for in-project`(): Unit = timeoutRunBlocking(5.minutes) {
    val baseDir = createPoetryProject(inProject = false)
    val inProjectSetting = runPoetry(baseDir.asEelOrJustPath(), "config", "virtualenvs.in-project").getOrThrow().trim()
    assumeFalse(inProjectSetting == "true", "the global poetry config of this machine sets virtualenvs.in-project")
    lockWithoutEnv(baseDir)

    val info = assertThat(PyPoetrySdkConfiguration().checkEnvironmentAndPrepareSdkCreator(pyProject, emptyList()))
      .asInstanceOf(type(CreateInterpreterInfo.WillCreateEnv::class.java))
      .actual()

    withCreatedSdk(baseDir, info) { sdkHome ->
      val cacheEnv = assertThat(poetryCacheEnvRoots(baseDir.toFileSystem(), baseDir).getOrThrow())
        .singleElement()
        .actual()
      assertThat(sdkHome).startsWithRaw(cacheEnv.path)
      assertThat(baseDir.resolve(".venv").exists()).isFalse()
    }
  }

  /** Writes a poetry project into the module. With [inProject], its `poetry.toml` sets `virtualenvs.in-project = true`. */
  private suspend fun createPoetryProject(inProject: Boolean): Path {
    val baseDir = pyProject.baseDir
    withContext(Dispatchers.IO) {
      baseDir.resolve(PY_PROJECT_TOML).writeText("""
        [tool.poetry]
        name = "in-project"
        version = "0.1.0"
        description = ""
        authors = []
        package-mode = false

        [tool.poetry.dependencies]
        python = ">=3.8"
      """.trimIndent())
    }
    if (inProject) {
      configurePoetryEnvironment(baseDir, "virtualenvs.in-project", "true", "--local")
    }
    withContext(Dispatchers.IO) {
      LocalFileSystem.getInstance().refreshAndFindFileByNioFile(baseDir.resolve(PY_PROJECT_TOML))
    }
    return baseDir
  }

  /** Writes `poetry.lock`. A plain `poetry lock` also creates the environment, and the test needs a project without one. */
  private suspend fun lockWithoutEnv(baseDir: Path) {
    runPoetry(baseDir.asEelOrJustPath(), "lock", baseEnv = listOf(EnvVar("POETRY_VIRTUALENVS_CREATE", "false"))).getOrThrow()
    assertThat(baseDir.resolve(".venv").exists()).isFalse()
    assertThat(poetryCacheEnvRoots(baseDir.toFileSystem(), baseDir).getOrThrow()).isEmpty()
  }

  private suspend fun assertInProjectAndNoCacheEnv(baseDir: Path, sdkHome: Path?) {
    assertThat(sdkHome).startsWithRaw(baseDir.resolve(".venv"))
    assertThat(poetryCacheEnvRoots(baseDir.toFileSystem(), baseDir).getOrThrow()).isEmpty()
  }

  /** Creates the SDK with [info], gives its home path to [assertions], then removes the SDK and the cache envs of poetry. */
  private suspend fun withCreatedSdk(
    baseDir: Path,
    info: CreateInterpreterInfoWithInterpreterCreator,
    assertions: suspend (sdkHome: Path?) -> Unit,
  ) {
    var sdk: Sdk? = null
    try {
      sdk = info.getInterpreterCreator(pyProject.residesOnModule)
        .createInterpreter()
        .getOrThrow()
        .getSdkAPI()
      assertions(sdk.homePath?.let(::Path))
    }
    finally {
      try {
        sdk?.let { removeTestSdkFromOpenProjects(it) }
      }
      finally {
        runPoetry(baseDir.asEelOrJustPath(), "env", "remove", "--all", inProjectEnv = false)
      }
    }
  }
}
