// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xml.tools

import com.intellij.openapi.application.EDT
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes

/**
 * @author Dmitry Avdeev
 */
@TestApplication
class GenerateXmlFromXsdTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture()
  }

  private val myFixture by lightJavaCodeInsightFixture(projectFixture, "/xml/tests/testData/generate")

  @Test
  fun testErrorMessage(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    val file = StandardFileSystems.local().findFileByPath("${myFixture.testDataPath}/vast4.xsd")
    withContext(Dispatchers.EDT) {
      val dialog = GenerateInstanceDocumentFromSchemaDialog(myFixture.project, file)
      try {
        GenerateInstanceDocumentFromSchemaAction.doAction(myFixture.project, dialog)
        throw AssertionError()
      }
      catch (e: Exception) {
        assertThat(e.message!!).contains("Could not find type 'VideoClicks_Base_type'")
      }
      Disposer.dispose(dialog.disposable)
    }
  }

  @Test
  fun testRelativePath(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    val file = StandardFileSystems.local().findFileByPath("${myFixture.testDataPath}/top/top.xsd")
    withContext(Dispatchers.EDT) {
      val dialog = GenerateInstanceDocumentFromSchemaDialog(myFixture.project, file)
      GenerateInstanceDocumentFromSchemaAction.doAction(myFixture.project, dialog)
      Disposer.dispose(dialog.disposable)
    }
  }
}