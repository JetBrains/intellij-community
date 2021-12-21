// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.externalSystemIntegration.output.quickfixes

import com.intellij.pom.java.LanguageLevel
import com.intellij.maven.testFramework.MavenTestCase

class JpsLanguageLevelQuickFixTest : MavenTestCase() {
  private val jpsLanguageLevelQuickFix = JpsLanguageLevelQuickFix()

  fun `test get laguage level 11`() {
    val level = jpsLanguageLevelQuickFix.getLanguageLevelFromError("warning: source release 11 requires target release 11")
    assertEquals(LanguageLevel.JDK_11, level)
  }

  fun `test get laguage level 5`() {
    val level = jpsLanguageLevelQuickFix.getLanguageLevelFromError("error: release version 5 not supported")
    assertEquals(LanguageLevel.JDK_1_5, level)
  }

  fun `test get laguage level 16`() {
    val level = jpsLanguageLevelQuickFix.getLanguageLevelFromError("error: invalid source release: 16")
    assertEquals(LanguageLevel.JDK_16, level)
  }

  fun `test get laguage level - no found`() {
    val level = jpsLanguageLevelQuickFix.getLanguageLevelFromError("error: invalid source release: asd")
    assertNull(level)
  }
}