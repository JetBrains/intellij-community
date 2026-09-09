// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.alsoWin.pyproject

import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.util.io.createDirectories
import com.jetbrains.python.venvReader.VirtualEnvReader
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assumptions.assumeThat
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createFile

/**
 * Tests `findPythonUsingDirectoryListing` with supplied child names (PY-91841).
 *
 * The subtree load calls it for every directory of a project. The names answer for almost every one of
 * them, so the filesystem is read only for the few that may hold an interpreter.
 *
 * A case forces the family of the system, so the host of the test never decides which layout is under test.
 */
@TestApplication
internal class PyFindPythonWithChildNamesTest {
  private val pathFixture = tempPathFixture()

  private val posix = VirtualEnvReader(isWindows = false)
  private val windows = VirtualEnvReader(isWindows = true)

  private fun newDir(name: String): Path = pathFixture.get().resolve(name).createDirectories()

  /**
   * The supplied names decide whether the method searches the filesystem.
   *
   * The same environment answers differently for two sets of names. Names that allow an interpreter reach
   * the disk and find it. Names that allow none answer without a read, so the interpreter on the disk stays
   * unseen. The form that takes no names always reads.
   */
  @Test
  fun testTheNamesDecideWhetherTheDiskIsRead() {
    val env = newDir("env")
    env.resolve("bin").createDirectories().resolve("python").createFile()

    assertThat(posix.findPythonUsingDirectoryListing(directory = env, childNames = sequenceOf("bin")))
      .describedAs("the names allow an interpreter, so the disk is read")
      .isNotNull()
    assertThat(posix.findPythonUsingDirectoryListing(directory = env, childNames = sequenceOf("src", "tests")))
      .describedAs("the names allow none, so the interpreter of the disk stays unseen")
      .isNull()
    assertThat(posix.findPythonUsingDirectoryListing(directory = env, childNames = emptySequence()))
      .describedAs("an empty listing skips the interpreter on disk")
      .isNull()
    assertThat(posix.findPythonInPythonRoot(env))
      .describedAs("the form without names always reads the disk")
      .isNotNull()
  }

  /** The first way: the binary sits under the directory of interpreters of the layout. */
  @Test
  fun testADirectoryOfInterpretersIsFound() {
    val posixEnv = newDir("posixEnv")
    posixEnv.resolve("bin").createDirectories().resolve("python").createFile()
    assertThat(posix.findPythonUsingDirectoryListing(directory = posixEnv, childNames = sequenceOf("bin"))).isNotNull()

    val windowsEnv = newDir("windowsEnv")
    windowsEnv.resolve("Scripts").createDirectories().resolve("python.exe").createFile()
    assertThat(windows.findPythonUsingDirectoryListing(directory = windowsEnv, childNames = sequenceOf("Scripts"))).isNotNull()
  }

  /** The second way: the binary lies directly in the directory, as in the root of an installation. */
  @Test
  fun testABinaryInTheDirectoryIsFound() {
    for (name in listOf("python", "python3", "python3.11", "pythonw", "pypy", "pypy3.10")) {
      val root = newDir("posix-$name")
      root.resolve(name).createFile()
      assertThat(posix.findPythonUsingDirectoryListing(directory = root, childNames = sequenceOf(name)))
        .describedAs("the posix layout holds '$name'")
        .isNotNull()
    }
    for (name in listOf("python.exe", "pythonw.exe", "python3.11.exe", "python_d.exe")) {
      val root = newDir("win-$name")
      root.resolve(name).createFile()
      assertThat(windows.findPythonUsingDirectoryListing(directory = root, childNames = sequenceOf(name)))
        .describedAs("the windows layout holds '$name'")
        .isNotNull()
    }
  }

  /**
   * `findPythonInPythonRoot` resolves the directory of interpreters with `Path.resolve`, and a filesystem
   * that ignores the case answers `resolve("bin")` with a directory named `Bin`. The names must allow that
   * one, or the interpreter stays unseen and the load takes every file of the environment.
   */
  @Test
  fun testTheCaseOfTheDirectoryNameFollowsTheFilesystem() {
    val env = newDir("caseEnv")
    env.resolve("Bin").createDirectories().resolve("python").createFile()
    assumeThat(Files.isDirectory(env.resolve("bin")))
      .describedAs("this filesystem keeps the case, so no interpreter is reachable as `bin`")
      .isTrue()

    assertThat(posix.findPythonUsingDirectoryListing(directory = env, childNames = sequenceOf("Bin"))).isNotNull()
  }

  /** The layout of the other system does not count, because one path reads one layout. */
  @Test
  fun testTheLayoutOfTheOtherSystemDoesNotCount() {
    val env = newDir("otherLayout")
    env.resolve("Scripts").createDirectories().resolve("python.exe").createFile()
    assertThat(posix.findPythonUsingDirectoryListing(directory = env, childNames = sequenceOf("Scripts")))
      .describedAs("the posix layout reads `bin` and no `Scripts`")
      .isNull()
  }

  @Test
  fun testAnOrdinaryDirectoryIsNotAnEnvironment() {
    val plain = newDir("plain")
    plain.resolve("src").createDirectories()
    plain.resolve("pyproject.toml").createFile()
    assertThat(posix.findPythonUsingDirectoryListing(directory = plain, childNames = sequenceOf("src", "pyproject.toml"))).isNull()
    assertThat(posix.findPythonUsingDirectoryListing(directory = plain, childNames = emptySequence()))
      .describedAs("no child at all")
      .isNull()
  }

  /** A name that only starts like a binary reaches no interpreter, and the pattern of the layout says so. */
  @Test
  fun testANameThatOnlyStartsLikeABinaryIsNotAnEnvironment() {
    val root = newDir("helper")
    root.resolve("python_helper").createFile()
    assertThat(posix.findPythonUsingDirectoryListing(directory = root, childNames = sequenceOf("python_helper"))).isNull()
  }
}
