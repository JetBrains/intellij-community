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
package com.intellij.html;

import com.intellij.codeInsight.documentation.DocumentationManager;
import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.webSymbols.testFramework.WebTestUtil;
import org.jetbrains.annotations.NotNull;

public class HtmlDocumentationTest extends BasePlatformTestCase {
  public void testQuickDocumentationHtml5Tag() {
    doTest("""
             <!DOCTYPE html>
             <html>
             <bo<caret>dy onload="">
             </body>
             </html>"""
    );
  }

  public void testQuickDocumentationHtml5TagDialog() {
    doTest("""
             <!DOCTYPE html>
             <html>
             <body onload="">
             <dia<caret>log></dialog
             </body>
             </html>"""
    );
  }

  public void testQuickDocumentationHtml5Attr() {
    doTest("""
             <!DOCTYPE html>
             <html>
             <body on<caret>load="">
             </body>
             </html>"""
    );
  }

  public void testQuickDocumentationHtml5Svg() {
    doTest("""
             <!DOCTYPE html>
             <html>
             <body>
             <sv<caret>g>
             </svg>
             </body>
             </html>"""
    );
  }

  public void testQuickDocumentationHtml5SvgImage() {
    doTest("""
             <!DOCTYPE html>
             <html>
             <body>
             <svg>
             <ima<caret>ge>
             </image>
             </svg>
             </body>
             </html>"""
    );
  }

  public void testQuickDocumentationHtml5Math() {
    doTest("""
             <!DOCTYPE html>
             <html>
             <body>
             <ma<caret>th>
             </math>
             </body>
             </html>"""
    );
  }

  public void testQuickDocumentationHtml5MathMrow() {
    doTest("""
             <!DOCTYPE html>
             <html>
             <body>
             <math>
             <mr<caret>ow>
             </mrow>
             </math>
             </body>
             </html>"""
    );
  }

  public void testQuickDocumentationHtml4Tag() {
    doTest("""
             <!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN"
                "http://www.w3.org/TR/html4/loose.dtd">
             <html>
             <bo<caret>dy onload="">
             </body>
             </html>"""
    );
  }

  public void testQuickDocumentationHtml4Attr() {
    doTest("""
             <!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN"
                "http://www.w3.org/TR/html4/loose.dtd">
             <html>
             <body on<caret>load="">
             </body>
             </html>"""
    );
  }

  public void testQuickDocumentationHtml5Script() {
    doTest("<scr<caret>ipt></script>"
    );
  }


  public void testQuickDocumentationHtml5MediaEvents() {
    doTest("<video on<caret>stalled=''>"
    );
  }

  public void testLookupDocWordCompletions() {
    myFixture.configureByText("test.html", "<html lang='en'>la<caret>n");
    PsiElement originalElement = myFixture.getFile().findElementAt(myFixture.getEditor().getCaretModel().getOffset());
    DocumentationProvider documentationProvider = DocumentationManager.getProviderFromElement(originalElement);
    PsiElement element = documentationProvider.getDocumentationElementForLookupItem(originalElement.getManager(), "lang", originalElement);
    assertNull(element);
  }

  private void doTest(String text) {
    myFixture.configureByText("test.html", text);
    PsiElement originalElement = myFixture.getFile().findElementAt(myFixture.getEditor().getCaretModel().getOffset());
    PsiElement element = DocumentationManager.getInstance(getProject()).findTargetElement(myFixture.getEditor(), myFixture.getFile());
    DocumentationProvider documentationProvider = DocumentationManager.getProviderFromElement(originalElement);

    String generatedDoc = documentationProvider.generateDoc(element, originalElement);
    if (generatedDoc == null) {
      generatedDoc = "<no documentation>";
    }
    generatedDoc = StringUtil.convertLineSeparators(generatedDoc.strip());
    generatedDoc += "\n---\n" + documentationProvider.getUrlFor(element, originalElement);
    WebTestUtil.checkTextByFile(myFixture, generatedDoc, getTestName(false) + ".expected.html");
  }

  @Override
  protected @NotNull String getTestDataPath() {
    return PlatformTestUtil.getCommunityPath() + "/xml/tests/testData/documentation/";
  }
}
