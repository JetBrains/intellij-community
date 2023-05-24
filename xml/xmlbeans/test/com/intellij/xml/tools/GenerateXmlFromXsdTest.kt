// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xml.tools

import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import junit.framework.TestCase
import org.assertj.core.api.Assertions.assertThat

/**
 * @author Dmitry Avdeev
 */
class GenerateXmlFromXsdTest: LightJavaCodeInsightFixtureTestCase() {
  fun testErrorMessage() {
    val file = LocalFileSystem.getInstance().findFileByPath("$testDataPath/vast4.xsd")
    val dialog = GenerateInstanceDocumentFromSchemaDialog(project, file)
    try {
      GenerateInstanceDocumentFromSchemaAction.doAction(project, dialog)
      TestCase.fail()
    }
    catch (e: Exception) {
      assertThat(e.message!!).contains("Could not find type 'VideoClicks_Base_type'")
    }
    Disposer.dispose(dialog.disposable)
  }

  fun testRelativePath() {
    val file = LocalFileSystem.getInstance().findFileByPath("$testDataPath/top/top.xsd")
    val dialog = GenerateInstanceDocumentFromSchemaDialog(project, file)
    GenerateInstanceDocumentFromSchemaAction.doAction(project, dialog)
    Disposer.dispose(dialog.disposable)
  }

  override fun getBasePath(): String = "/xml/tests/testData/generate"
}