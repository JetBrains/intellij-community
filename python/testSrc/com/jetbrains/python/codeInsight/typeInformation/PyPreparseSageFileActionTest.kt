// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.typeInformation

import com.intellij.idea.TestFor
import com.intellij.testFramework.LightVirtualFile
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@TestFor(classes = [PyPreparseSageFileAction::class])
@Subsystems.CodeInsight
@Layers.Functional
class PyPreparseSageFileActionTest : PyCodeInsightTestCase() {
  @Test
  fun `recognizes sage files`() {
    assertTrue(isSageFile(LightVirtualFile("test.sage")))
    assertFalse(isSageFile(LightVirtualFile("test.py")))
    assertFalse(isSageFile(LightVirtualFile("test.sage.py")))
  }

  @Test
  fun `detects the conversion marker`() {
    assertTrue(
      hasSageConversionMarker(
        "# Converted by sage-pycharm-stubgen. Remove this line to re-convert Sage syntax.\n" +
        "from sage.all import *\n"
      )
    )
    assertFalse(hasSageConversionMarker("from sage.all import *\n"))
    assertFalse(hasSageConversionMarker("R.<x> = GF(2)[]\n"))
  }
}
