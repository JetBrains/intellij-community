// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit

import com.intellij.openapi.Disposable
import com.intellij.openapi.vcs.LocalFilePath
import com.intellij.pycharm.community.ide.impl.configuration.PyTemporarilyIgnoredFileProvider
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.pathString

@TestApplication
class PyTemporarilyIgnoredFileProviderTest {
  private val project = projectFixture()

  @Test
  fun testExclude(@TempDir path: Path, @TestDisposable disable: Disposable) {

    val ignored = path.resolve("ignored")

    PyTemporarilyIgnoredFileProvider.ignoreRoot(ignored, disable)
    val sut = PyTemporarilyIgnoredFileProvider()

    assertTrue(sut.isIgnoredFile(project.get(), LocalFilePath(ignored.pathString, true)))
    assertTrue(sut.isIgnoredFile(project.get(), LocalFilePath(ignored.resolve("1.txt").pathString, false)))
    assertFalse(sut.isIgnoredFile(project.get(), LocalFilePath(path.pathString, true)))
  }
}