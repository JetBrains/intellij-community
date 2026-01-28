/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.html

import com.intellij.codeInsight.documentation.DocumentationManager
import com.intellij.openapi.util.text.StringUtil
import com.intellij.polySymbols.testFramework.checkDocumentationAtCaret
import com.intellij.polySymbols.testFramework.checkLookupItems
import com.intellij.polySymbols.testFramework.checkTextByFile
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class HtmlDocumentationTest : BasePlatformTestCase() {
  fun testQuickDocumentationHtml5Tag() {
    doTest(
      """
      <!DOCTYPE html>
      <html>
      <bo<caret>dy onload="">
      </body>
      </html>
      """.trimIndent()
    )
  }

  fun testQuickDocumentationHtml5TagDialog() {
    doTest(
      """
      <!DOCTYPE html>
      <html>
      <body onload="">
      <dia<caret>log></dialog
      </body>
      </html>
      """.trimIndent()
    )
  }

  fun testQuickDocumentationHtml5Attr() {
    doTest(
      """
      <!DOCTYPE html>
      <html>
      <body on<caret>load="">
      </body>
      </html>
      """.trimIndent()
    )
  }

  fun testQuickDocumentationHtml5Svg() {
    doTest(
      """
      <!DOCTYPE html>
      <html>
      <body>
      <sv<caret>g>
      </svg>
      </body>
      </html>
      """.trimIndent()
    )
  }

  fun testQuickDocumentationHtml5SvgImage() {
    doTest(
      """
      <!DOCTYPE html>
      <html>
      <body>
      <svg>
      <ima<caret>ge>
      </image>
      </svg>
      </body>
      </html>
      """.trimIndent()
    )
  }

  fun testQuickDocumentationHtml5Math() {
    doTest(
      """
      <!DOCTYPE html>
      <html>
      <body>
      <ma<caret>th>
      </math>
      </body>
      </html>
      """.trimIndent()
    )
  }

  fun testQuickDocumentationHtml5MathMrow() {
    doTest(
      """
      <!DOCTYPE html>
      <html>
      <body>
      <math>
      <mr<caret>ow>
      </mrow>
      </math>
      </body>
      </html>
      """.trimIndent()
    )
  }

  fun testQuickDocumentationHtml4Tag() {
    doTest(
      """
      <!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN"
         "http://www.w3.org/TR/html4/loose.dtd">
      <html>
      <bo<caret>dy onload="">
      </body>
      </html>
      """.trimIndent()
    )
  }

  fun testQuickDocumentationHtml4Attr() {
    doTest(
      """
      <!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN"
         "http://www.w3.org/TR/html4/loose.dtd">
      <html>
      <body on<caret>load="">
      </body>
      </html>
      """.trimIndent()
    )
  }

  fun testQuickDocumentationHtml5Script() {
    doTest(
      "<scr<caret>ipt></script>"
    )
  }


  fun testQuickDocumentationHtml5MediaEvents() {
    doTest(
      "<video on<caret>stalled=''>"
    )
  }

  fun testAttributeQuickDocAtTheEndOfFile() {
    myFixture.configureByText("attributeQuickDocAtTheEndOfFile.html", "<button popovert<caret>")
    myFixture.checkLookupItems(
      checkDocumentation = true,
      fileName = "attributeQuickDocAtTheEndOfFile",
    ) { true }
  }

  fun testInputAttributeQuickDoc() {
    myFixture.configureByText("attributeQuickDocAtTheEndOfFile.html", "<input type='button' popover<caret>targetaction>")
    myFixture.checkDocumentationAtCaret()
  }

  fun testLookupDocWordCompletions() {
    myFixture.configureByText("test.html", "<html lang='en'>la<caret>n")
    val originalElement = myFixture.getFile().findElementAt(myFixture.getEditor().getCaretModel().getOffset())
    val documentationProvider = DocumentationManager.getProviderFromElement(originalElement)
    val element = documentationProvider.getDocumentationElementForLookupItem(originalElement!!.getManager(), "lang", originalElement)
    assertNull(element)
  }

  private fun doTest(text: String) {
    myFixture.configureByText("test.html", text)
    val originalElement = myFixture.getFile().findElementAt(myFixture.getEditor().getCaretModel().getOffset())
    val element = DocumentationManager.getInstance(getProject()).findTargetElement(myFixture.getEditor(), myFixture.getFile())
    val documentationProvider = DocumentationManager.getProviderFromElement(originalElement)

    var generatedDoc = documentationProvider.generateDoc(element, originalElement)
    if (generatedDoc == null) {
      generatedDoc = "<no documentation>"
    }
    generatedDoc = StringUtil.convertLineSeparators(generatedDoc.trim())
    generatedDoc += "\n---\n" + documentationProvider.getUrlFor(element, originalElement)
    myFixture.checkTextByFile(generatedDoc, getTestName(false) + ".expected.html")
  }

  protected override fun getTestDataPath(): String {
    return PlatformTestUtil.getCommunityPath() + "/xml/tests/testData/documentation/"
  }
}
