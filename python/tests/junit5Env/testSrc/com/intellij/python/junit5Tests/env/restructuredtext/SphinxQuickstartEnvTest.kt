// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.env.restructuredtext

import com.intellij.idea.TestFor
import com.intellij.python.community.execService.Args
import com.intellij.python.community.execService.ExecService
import com.intellij.python.junit5Tests.framework.env.PyEnvTestCase
import com.intellij.python.junit5Tests.framework.env.pyEnvironmentFixture
import com.intellij.python.junit5Tests.framework.pyProjectFixture
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.test.env.junit5.pyVenvFixture
import com.intellij.restructuredtext.python.runSphinxQuickstart
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.jetbrains.python.getOrThrow
import com.jetbrains.python.sdk.execGetStdout
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.seconds

@TestFor(issues = ["PY-93936"])
@PyEnvTestCase
internal class SphinxQuickstartEnvTest {
  private val tempPathFixture = tempPathFixture()
  private val docsDirFixture = tempPathFixture()
  private val projectFixture = projectFixture()
  private val pyProjectFixture = projectFixture.pyProjectFixture(tempPathFixture)
  private val venvFixture = pyEnvironmentFixture().pyVenvFixture(where = tempPathFixture, pyProjectFixture = pyProjectFixture)

  @Test
  fun `the quickstart asks its questions in the interpreter and adds only new files to the docs directory`(): Unit = timeoutRunBlocking {
    val venv = venvFixture.get()
    installQuickstartStub(venv)
    val docsDir = docsDirFixture.get()
    (docsDir / "index.rst").writeText("user text\n")
    (docsDir / "notes.txt").writeText("user notes\n")

    val exitCode = runQuickstart(venv, docsDir, projectName = "Docs")

    assertEquals(0, exitCode)
    assertEquals("project = 'Docs'\n", (docsDir / "conf.py").readText())
    assertEquals("user text\n", (docsDir / "index.rst").readText())
    assertEquals("user notes\n", (docsDir / "notes.txt").readText())
  }

  @Test
  fun `the quickstart adds no file to the docs directory after a nonzero exit code`(): Unit = timeoutRunBlocking {
    val venv = venvFixture.get()
    installQuickstartStub(venv)
    val docsDir = docsDirFixture.get()

    val exitCode = runQuickstart(venv, docsDir, projectName = FAILING_PROJECT_NAME)

    assertEquals(1, exitCode)
    assertFalse((docsDir / "conf.py").exists())
    assertFalse((docsDir / "index.rst").exists())
  }

  private suspend fun runQuickstart(venv: PythonInterpreter, docsDir: Path, projectName: String): Int =
    withTimeout(30.seconds) {
      runSphinxQuickstart(venv, docsDir) { handler ->
        handler.startNotify()
        handler.processInput!!.writer(Charsets.UTF_8).apply {
          write("$projectName\n")
          flush()
        }
      }.getOrThrow()
    }

  /** Puts a `sphinx.cmd.quickstart` module into the site packages of [venv], so the test needs no network. */
  private suspend fun installQuickstartStub(venv: PythonInterpreter) {
    val sitePackages = ExecService().execGetStdout(venv, Args("-c", "import sysconfig; print(sysconfig.get_paths()['purelib'])"))
      .getOrThrow().trim()
    val cmdDir = (Path.of(sitePackages) / "sphinx" / "cmd").createDirectories()
    (cmdDir.parent / "__init__.py").writeText("")
    (cmdDir / "__init__.py").writeText("")
    (cmdDir / "quickstart.py").writeText("""
      import os
      import sys

      name = input("> Project name: ")
      with open(os.path.join(sys.argv[1], "conf.py"), "w", encoding="utf-8", newline="\n") as conf:
          conf.write(f"project = {name!r}\n")
      with open(os.path.join(sys.argv[1], "index.rst"), "w", encoding="utf-8", newline="\n") as index:
          index.write("generated\n")
      sys.exit(1 if name == "$FAILING_PROJECT_NAME" else 0)
    """.trimIndent())
  }
}

/** The project name that makes the quickstart stub write its files and then end with exit code 1. */
private const val FAILING_PROJECT_NAME = "Fail"
