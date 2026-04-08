// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.typing

import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class PyTypedFileTypeTest : BasePlatformTestCase() {

  fun testPyTypedUsesDedicatedFileType() {
    val pyTypedFileType = FileTypeManager.getInstance().findFileTypeByName("PyTyped")
    assertNotNull(pyTypedFileType)
    assertInstanceOf(pyTypedFileType, LanguageFileType::class.java)
    assertEquals(pyTypedFileType, FileTypeManager.getInstance().getFileTypeByFileName("py.typed"))

    val psiFile = myFixture.configureByText("py.typed", "partial")
    assertEquals(pyTypedFileType, psiFile.fileType)
    assertEquals(PlainTextLanguage.INSTANCE, (pyTypedFileType as LanguageFileType).language)
    assertEquals(PlainTextLanguage.INSTANCE, psiFile.language)
  }
}
