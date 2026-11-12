// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package de.plushnikov.intellij.plugin.action.lombok

import com.intellij.lombok.backend.action.lombok.LombokEqualsAndHashcodeAction
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import de.plushnikov.intellij.plugin.LombokTestUtil
import de.plushnikov.intellij.plugin.lombokActionFixture
import org.junit.jupiter.api.Test

@TestApplication
class LombokEqualsAndHashcodeActionTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture(LombokTestUtil.LOMBOK_JAVA21_DESCRIPTOR)
  }

  private val lombokAction by lombokActionFixture(projectFixture, "/action/lombok/equalsAndHashcode") { LombokEqualsAndHashcodeAction() }

  @Test
  fun testEqualsAndHashcodeSimple() {
    lombokAction.doTest()
  }
}
