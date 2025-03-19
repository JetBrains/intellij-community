// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit

import com.intellij.python.community.services.internal.impl.PythonWithLanguageLevelImpl
import com.intellij.testFramework.junit5.TestApplication
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
      PythonWithLanguageLevelImpl(path, LanguageLevel.PYTHON38),
      PythonWithLanguageLevelImpl(path, LanguageLevel.PYTHON312),
      PythonWithLanguageLevelImpl(path, LanguageLevel.PYTHON311),
    )

    val sortedLevels = list.sorted().map { it.languageLevel }.toTypedArray()
    assertArrayEquals(arrayOf(LanguageLevel.PYTHON312, LanguageLevel.PYTHON311, LanguageLevel.PYTHON38), sortedLevels,
                      "Highest python goes first")
  }
}