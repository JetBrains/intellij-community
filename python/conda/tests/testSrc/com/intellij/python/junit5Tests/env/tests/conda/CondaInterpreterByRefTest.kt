// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.env.tests.conda

import com.intellij.openapi.util.SystemInfo
import com.intellij.python.community.impl.conda.CondaPyTool
import com.intellij.python.community.junit5Tests.framework.conda.CondaEnv
import com.intellij.python.community.junit5Tests.framework.conda.PyEnvTestCaseWithConda
import com.intellij.python.junit5Tests.framework.pyProjectFixture
import com.intellij.python.sdk.backend.PythonInterpreterRegistry
import com.intellij.python.pyproject.model.evolution.setPythonInterpreter
import com.intellij.python.sdk.backend.flavor
import com.intellij.python.sdk.backend.getPythonInfo
import com.intellij.python.sdk.common.PyEnvRef
import com.intellij.python.sdk.common.PyInterpreterRef
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.jetbrains.python.conda.savePythonCondaPath
import com.jetbrains.python.getOrThrow
import com.jetbrains.python.project.project
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.sdk.flavors.conda.CondaEnvSdkFlavor
import com.jetbrains.python.sdk.flavors.conda.NewCondaEnvRequest
import com.jetbrains.python.sdk.flavors.conda.PyCondaCommand
import com.jetbrains.python.sdk.flavors.conda.PyCondaEnv
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.minutes

/**
 * PY-93079: an interpreter is added by its ref right after its conda env is created. The conda env list is cached, and
 * the cache was filled before the env existed. The ref must still find the new env.
 */
@PyEnvTestCaseWithConda
internal class CondaInterpreterByRefTest {
  private val projectFixture = projectFixture()
  private val pyProjectFixture = projectFixture.pyProjectFixture()

  private lateinit var conda: String
  private val namedEnvs = mutableListOf<String>()

  @BeforeEach
  fun setUp(@CondaEnv baseEnv: PyCondaEnv) {
    conda = baseEnv.fullCondaPathOnTarget
    savePythonCondaPath(Path.of(conda))
  }

  @AfterEach
  fun tearDown(): Unit = timeoutRunBlocking(10.minutes) {
    for (name in namedEnvs) {
      runConda("env", "remove", "-y", "-n", name)
    }
  }

  @Test
  fun `new named env is found by its ref while the env list is cached`(): Unit = timeoutRunBlocking(10.minutes) {
    val command = PyCondaCommand(conda, null)
    // Fills the cache before the env exists, as the New Project dialog does.
    PyCondaEnv.getEnvs(command.asBinaryToExec(), forceRefresh = true).getOrThrow()
    val name = "py93079_${System.currentTimeMillis()}"
    namedEnvs += name
    PyCondaEnv.createEnv(command, NewCondaEnvRequest.EmptyNamedEnv(LanguageLevel.PYTHON311, name)).getOrThrow()

    val pyProject = pyProjectFixture.get()
    val ref = PyInterpreterRef.native(CondaPyTool.getInstance().fusId, PyEnvRef(name))
    val registry = PythonInterpreterRegistry.getInstance(pyProject.project)
    val interpreter = pyProject.setPythonInterpreter(registry.newPythonInterpreter(pyProject, ref).getOrThrow()).getOrThrow()!!
    try {
      assertThat(interpreter.ref).isEqualTo(ref)
      assertThat(interpreter.flavor).isEqualTo(CondaEnvSdkFlavor)
      // The env has its own Python, so the interpreter is the new env and not the base env.
      assertThat(interpreter.getPythonInfo().getOrThrow().languageLevel).isEqualTo(LanguageLevel.PYTHON311)
    }
    finally {
      pyProject.setPythonInterpreter(null).getOrThrow()
      registry.removePythonInterpreter(pyProject, interpreter)
    }
  }

  private suspend fun runConda(vararg args: String): Pair<Int, String> = withContext(Dispatchers.IO) {
    val command = if (SystemInfo.isWindows && conda.endsWith(".bat")) listOf("cmd", "/c", conda) + args else listOf(conda) + args
    val process = ProcessBuilder(command).redirectErrorStream(true).start()
    val output = process.inputStream.bufferedReader().readText()
    if (!process.waitFor(10, TimeUnit.MINUTES)) process.destroyForcibly()
    process.exitValue() to output
  }
}
