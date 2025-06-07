// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.alsoWin.services.internal.impl

import com.intellij.python.community.services.internal.impl.PythonWithLanguageLevelImpl
import com.intellij.testFramework.junit5.TestApplication
import com.jetbrains.python.psi.LanguageLevel
import kotlinx.coroutines.runBlocking
import org.hamcrest.CoreMatchers.allOf
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.matchesPattern
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.pathString

@TestApplication
class ReadableNameTest {
  private companion object {
    const val PYTHON_FILE_NAME = "fakepython"
  }

  @Test
  fun testNoHomePath(@TempDir path: Path): Unit = runBlocking {
    val fakePython = path.resolve(PYTHON_FILE_NAME)
    val name = PythonWithLanguageLevelImpl(fakePython, LanguageLevel.PYTHON312).getReadableName()
    assertThat("Wrong name generated", name, allOf(containsString("3.12"), containsString(fakePython.pathString)))
  }

  @Test
  fun testHomePath(): Unit = runBlocking {
    val home = Path.of(System.getProperty("user.home"))


    var fakePython = home.resolve(PYTHON_FILE_NAME)
    var name = PythonWithLanguageLevelImpl(fakePython, LanguageLevel.PYTHON312).getReadableName()
    assertThat("Wrong name generated", name, allOf(containsString("3.12"), matchesPattern(".*~[\\\\/]$PYTHON_FILE_NAME.*")))

    fakePython = home.resolve("deep").resolve(PYTHON_FILE_NAME)
    name = PythonWithLanguageLevelImpl(fakePython, LanguageLevel.PYTHON312).getReadableName()
    assertThat("Wrong name generated", name, allOf(containsString("3.12"), matchesPattern(".*~[\\\\/]deep[\\\\/]$PYTHON_FILE_NAME.*")))
  }
}