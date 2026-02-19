// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit

import com.intellij.python.community.services.internal.impl.VanillaPythonWithPythonInfoImpl
import com.intellij.testFramework.junit5.TestApplication
import com.jetbrains.python.PythonInfo
import com.jetbrains.python.psi.LanguageLevel
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

@TestApplication
class CompareByLanguageLevelTest {
  @Test
  fun testCompareByLanguageLevel(@TempDir path: Path) {
    val list = listOf(
      VanillaPythonWithPythonInfoImpl(path, PythonInfo(LanguageLevel.PYTHON38)),
      VanillaPythonWithPythonInfoImpl(path, PythonInfo(LanguageLevel.PYTHON312)),
      VanillaPythonWithPythonInfoImpl(path, PythonInfo(LanguageLevel.PYTHON311)),
    )

    val sortedLevels = list.sorted().map { it.pythonInfo.languageLevel }.toTypedArray()
    assertArrayEquals(arrayOf(LanguageLevel.PYTHON312, LanguageLevel.PYTHON311, LanguageLevel.PYTHON38), sortedLevels,
                      "Highest python goes first")
  }
}