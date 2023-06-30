// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.runModalTask
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.utils.io.createDirectory
import com.intellij.testFramework.utils.io.createFile
import org.jetbrains.plugins.terminal.exp.completion.IJShellRuntimeDataProvider
import org.jetbrains.plugins.terminal.exp.util.TerminalSessionTestUtil
import org.junit.Assume
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.io.File
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.io.path.createTempDirectory

@RunWith(JUnit4::class)
class ShellRuntimeDataProviderTest : BasePlatformTestCase() {
  private lateinit var session: TerminalSession
  private lateinit var testDirectory: Path
  private val shellPath = "/bin/zsh"

  override fun setUp() {
    Assume.assumeTrue("Shell is not found in '$shellPath'", File(shellPath).exists())
    super.setUp()
    session = TerminalSessionTestUtil.startTerminalSession(project, shellPath, testRootDisposable)
    testDirectory = createTempDirectory(prefix = "runtime_data")
  }

  override fun tearDown() {
    if (!this::session.isInitialized) {
      return // no shell installed
    }
    try {
      Disposer.dispose(session)
      FileUtil.deleteRecursively(testDirectory)
    }
    catch (t: Throwable) {
      addSuppressedException(t)
    }
    finally {
      super.tearDown()
    }
  }

  @Test
  fun `get all files from directory`() {
    val expected = listOf(
      file("abcde.txt"),
      file("aghsdml"),
      file("bcde.zip"),
      directory("acde"),
      file("aeufsgf"),
      directory("cedrtuysa"),
      directory("aftyrt")
    )
    expected.forEach { it.create(testDirectory) }

    val provider = IJShellRuntimeDataProvider(session)
    val future = CompletableFuture<Boolean>()
    try {
      ApplicationManager.getApplication().executeOnPooledThread {
        runModalTask("test", project) {
          val actual = provider.getFilesFromDirectory(testDirectory.toString())
          UsefulTestCase.assertSameElements(actual, expected.map { it.toString() } + listOf("./", "../"))
          future.complete(true)
        }
      }
      future.get(5000, TimeUnit.MILLISECONDS)
    }
    catch (t: TimeoutException) {
      fail("Failed to finish in time. Probably something went wrong.")
    }
  }

  private data class FileDescriptor(val name: String, val isDirectory: Boolean = false) {
    override fun toString(): String = if (isDirectory) "$name/" else name

    fun create(basePath: Path) {
      if (isDirectory) basePath.createDirectory(name) else basePath.createFile(name)
    }
  }

  private fun file(name: String): FileDescriptor = FileDescriptor(name)
  private fun directory(name: String): FileDescriptor = FileDescriptor(name, isDirectory = true)
}