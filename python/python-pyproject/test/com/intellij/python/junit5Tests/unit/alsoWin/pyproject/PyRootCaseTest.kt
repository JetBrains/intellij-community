// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.alsoWin.pyproject

import com.intellij.python.pyproject.PY_PROJECT_TOML
import com.intellij.python.pyproject.model.internal.platformBridge.refreshProjectRootsIntoVfs
import com.intellij.python.pyproject.model.internal.pyProjectToml.findPyProjectTomlFilesInIndex
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.util.io.createDirectories
import org.assertj.core.api.Assumptions.assumeThat
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createFile
import kotlin.io.path.name

/**
 * The search must accept a root whose case differs from the case on the disk (PY-91841).
 *
 * `projectBasePath` holds the path that opened the project, and a hit of the index carries the path of the
 * VFS. The two can differ in the case of a name, and a filesystem that ignores the case still resolves
 * both to one directory. `isUnder` therefore reads `SystemInfoRt.isFileSystemCaseSensitive`.
 *
 * `Path.startsWith` of nio would reject such a root, because `UnixPath` compares the bytes of a name and
 * reads no property of the filesystem. This test fails for that implementation.
 *
 * A filesystem that keeps the case makes two such paths two directories, so the case is skipped there.
 */
@TestApplication
internal class PyRootCaseTest {
  private val pathFixture = tempPathFixture()
  private val projectFixture = projectFixture(pathFixture)

  @Test
  fun testARootOfAnotherCaseStillFindsTheFile(): Unit = timeoutRunBlocking {
    val root = pathFixture.get()
    val member = root.resolve("Member")
    val toml = member.createDirectories().resolve(PY_PROJECT_TOML).createFile()

    val otherCase = root.resolve(member.name.lowercase())
    assumeThat(Files.isDirectory(otherCase))
      .describedAs("this filesystem keeps the case, so the two paths are two directories")
      .isTrue()

    refreshProjectRootsIntoVfs(projectFixture.get())

    val files: List<Path> = findPyProjectTomlFilesInIndex(setOf(otherCase), excludedPaths = emptySet())
    assertThat(files)
      .describedAs("a root of another case must still reach the file, as the filesystem does")
      .contains(toml)
  }
}
