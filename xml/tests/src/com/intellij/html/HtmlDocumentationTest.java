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
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;

public class HtmlDocumentationTest extends LightPlatformCodeInsightTestCase {
  public void testQuickDocumentationHtml5Tag() {
    doTest("<!DOCTYPE html>\n" +
           "<html>\n" +
           "<bo<caret>dy onload=\"\">\n" +
           "</body>\n" +
           "</html>");
  }

  public void testQuickDocumentationHtml5TagDialog() {
    doTest("<!DOCTYPE html>\n" +
           "<html>\n" +
           "<body onload=\"\">\n" +
           "<dia<caret>log></dialog\n" +
           "</body>\n" +
           "</html>");
  }

  public void testQuickDocumentationHtml5Attr() {
    doTest("<!DOCTYPE html>\n" +
           "<html>\n" +
           "<body on<caret>load=\"\">\n" +
           "</body>\n" +
           "</html>");
  }

  public void testQuickDocumentationHtml4Tag() {
    doTest("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01 Transitional//EN\"\n" +
           "   \"http://www.w3.org/TR/html4/loose.dtd\">\n" +
           "<html>\n" +
           "<bo<caret>dy onload=\"\">\n" +
           "</body>\n" +
           "</html>");
  }

  public void testQuickDocumentationHtml4Attr() {
    doTest("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01 Transitional//EN\"\n" +
           "   \"http://www.w3.org/TR/html4/loose.dtd\">\n" +
           "<html>\n" +
           "<body on<caret>load=\"\">\n" +
           "</body>\n" +
           "</html>");
  }

  private static void doTest(String text) {
    configureFromFileText("test.html", text);
    PsiElement originalElement = getFile().findElementAt(myEditor.getCaretModel().getOffset());
    PsiElement element = DocumentationManager.getInstance(getProject()).findTargetElement(getEditor(), getFile());
    DocumentationProvider documentationProvider = DocumentationManager.getProviderFromElement(originalElement);

    assertNotNull("inline help is null", documentationProvider.generateDoc(element, originalElement));
    assertNotNull("external help is null", documentationProvider.getUrlFor(element, originalElement));
  }
}
