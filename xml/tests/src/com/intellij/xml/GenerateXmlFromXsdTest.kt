/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.xml

import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import com.intellij.xml.actions.xmlbeans.GenerateInstanceDocumentFromSchemaAction
import com.intellij.xml.actions.xmlbeans.GenerateInstanceDocumentFromSchemaDialog
import junit.framework.TestCase

/**
 * @author Dmitry Avdeev
 */
class GenerateXmlFromXsdTest: LightCodeInsightFixtureTestCase() {
  fun testErrorMessage() {
    val file = LocalFileSystem.getInstance().findFileByPath(testDataPath + "/vast4.xsd")
    val dialog = GenerateInstanceDocumentFromSchemaDialog(project, file)
    try {
      GenerateInstanceDocumentFromSchemaAction.doAction(project, dialog)
      TestCase.fail()
    }
    catch (e: Exception) {
      TestCase.assertTrue(e.message!!.contains("vast4.xsd:192:13: error: src-resolve.a: Could not find type 'VideoClicks_Base_type'"))
    }
    Disposer.dispose(dialog.disposable)
  }


  override fun getBasePath(): String = "/xml/tests/testData/generate"
}