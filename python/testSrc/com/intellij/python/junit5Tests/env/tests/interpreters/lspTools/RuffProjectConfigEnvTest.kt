// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.env.tests.interpreters.lspTools

import com.intellij.idea.TestFor
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.components.service
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspClientManager
import com.intellij.platform.lsp.api.LspServerState
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightFixture
import com.intellij.python.junit5Tests.framework.env.PyEnvTestCase
import com.intellij.python.junit5Tests.framework.env.pySdkFixture
import com.intellij.python.junit5Tests.framework.pyModuleFixture
import com.intellij.python.junit5Tests.framework.pyProjectFixture
import com.intellij.python.ruff.RuffConfiguration
import com.intellij.python.ruff.RuffPyTool
import com.intellij.python.ruff.server.RuffLspIntegrationProvider
import com.intellij.python.test.env.junit5.pyVenvFixture
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.io.path.exists
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.milliseconds

/**
 * Ruff in a module with a content root outside the project directory, against the real `ruff` executable.
 *
 * The project directory is the first content root, and it holds a `ruff.toml` that asks for single quotes. The second
 * content root has no Ruff config, so Ruff alone would format it with its default double quotes. The third content
 * root has a config of its own, so the project config must not reach it.
 */
@TestFor(issues = ["PY-85409"])
@Subsystems.LspTools
@Layers.Functional
@TestApplication
@PyEnvTestCase
@Timeout(value = 10, unit = TimeUnit.MINUTES)
class RuffProjectConfigEnvTest {
  @BeforeEach
  fun setUp(): Unit = timeoutRunBlocking(timeout = 5.minutes) {
    writeProjectConfig(SINGLE_QUOTES)
    if (rootsAdded.compareAndSet(false, true)) {
      ownRoot.resolve("ruff.toml").writeText("line-length = 100\n")
      refreshed(secondRoot)
      refreshed(ownRoot)
      withContext(Dispatchers.EDT) {
        ModuleRootModificationUtil.addContentRoot(module, secondRoot.toString())
        ModuleRootModificationUtil.addContentRoot(module, ownRoot.toString())
      }
      assertEquals(3, ModuleRootManager.getInstance(module).contentRoots.size)
    }
    pyProject.enableLspToolAndInstall(project, RuffPyTool.getInstance(), toolInstalled) {
      project.service<RuffConfiguration>().formatting = true
    }
  }

  @Test
  fun `the server formats a root without a config with the project config`(): Unit = timeoutRunBlocking(timeout = 5.minutes) {
    project.service<RuffConfiguration>().formatSortImports = false
    assertEquals("x = 'a'\n", reformat(secondRoot.resolve("server.py"), "x = \"a\"\n"))
  }

  @Test
  fun `the command line formats a root without a config with the project config`(): Unit = timeoutRunBlocking(timeout = 5.minutes) {
    // This option sends the reformat to `RuffFormattingService`, which runs the Ruff executable.
    project.service<RuffConfiguration>().formatSortImports = true
    assertEquals("x = 'a'\n", reformat(secondRoot.resolve("cli.py"), "x = \"a\"\n"))
  }

  @Test
  fun `a root with its own config keeps it`(): Unit = timeoutRunBlocking(timeout = 5.minutes) {
    project.service<RuffConfiguration>().formatSortImports = false
    assertEquals("x = \"a\"\n", reformat(ownRoot.resolve("server.py"), "x = 'a'\n"))

    project.service<RuffConfiguration>().formatSortImports = true
    assertEquals("x = \"a\"\n", reformat(ownRoot.resolve("cli.py"), "x = 'a'\n"))
  }

  @Test
  fun `a change of the project config restarts the server`(): Unit = timeoutRunBlocking(timeout = 5.minutes) {
    project.service<RuffConfiguration>().formatSortImports = false
    assertEquals("x = 'a'\n", reformat(secondRoot.resolve("before.py"), "x = \"a\"\n"))
    val before = LspClientManager.getInstance(project).getClients(RuffLspIntegrationProvider::class.java).single()

    // The server reads the project config only when it starts. Without a restart it keeps the single quotes.
    writeProjectConfig(DOUBLE_QUOTES)
    withTimeout(2.minutes) {
      while (true) {
        val client = LspClientManager.getInstance(project).getClients(RuffLspIntegrationProvider::class.java).singleOrNull()
        if (client != null && client !== before && client.state == LspServerState.Running) break
        delay(200.milliseconds)
      }
    }
    assertEquals("x = \"b\"\n", reformat(secondRoot.resolve("after.py"), "x = 'b'\n"))
  }

  @AfterEach
  fun tearDownTool(): Unit = timeoutRunBlocking {
    tearDownLspTool(project, RuffLspIntegrationProvider::class.java)
  }

  /** Writes [text] to the file at [path], opens it, and answers the text after a reformat. */
  private suspend fun reformat(path: Path, text: String): String {
    path.writeText(text)
    val file = refreshed(path)
    withContext(Dispatchers.EDT) { editorFixture.configureFromExistingVirtualFile(file) }
    awaitFileOpenedByLspTool(project, file)
    editorFixture.performEditorAction(IdeActions.ACTION_EDITOR_REFORMAT)
    return withContext(Dispatchers.EDT) { editorFixture.editor.document.text }
  }

  private suspend fun writeProjectConfig(text: String) {
    val config = projectDir.resolve("ruff.toml")
    if (!config.exists()) config.writeText("")
    val file = refreshed(config)
    edtWriteAction { VfsUtil.saveText(file, text) }
  }

  private fun refreshed(path: Path): VirtualFile {
    val file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path)!!
    file.refresh(false, false)
    return file
  }

  companion object {
    private const val SINGLE_QUOTES = "[format]\nquote-style = \"single\"\n"
    private const val DOUBLE_QUOTES = "[format]\nquote-style = \"double\"\n"

    private val toolInstalled = AtomicBoolean(false)
    private val rootsAdded = AtomicBoolean(false)

    private val projectDirFixture = tempPathFixture()
    private val secondRootFixture = tempPathFixture()
    private val ownRootFixture = tempPathFixture()
    private val projectFixture = projectFixture(projectDirFixture, openAfterCreation = true)
    private val moduleFixture = projectFixture.pyModuleFixture(projectDirFixture, addPathToSourceRoot = true)
    private val venvFixture = pySdkFixture().pyVenvFixture(where = projectDirFixture, addToSdkTable = true, moduleFixture = moduleFixture)
    private val codeInsightFixtureFixture = codeInsightFixture(projectFixture, projectDirFixture)

    private val projectDir by projectDirFixture
    private val secondRoot by secondRootFixture
    private val ownRoot by ownRootFixture
    private val project by projectFixture
    private val module by moduleFixture
    private val pyProject by moduleFixture.pyProjectFixture()
    @Suppress("unused")
    private val venv by venvFixture
    private val editorFixture by codeInsightFixtureFixture
  }
}
