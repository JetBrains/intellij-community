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

import java.util.Collections;
import java.util.List;

public class HtmlDocumentationTest extends LightPlatformCodeInsightTestCase {
  public void testQuickDocumentationHtml5Tag() {
    doTest("<!DOCTYPE html>\n" +
           "<html>\n" +
           "<bo<caret>dy onload=\"\">\n" +
           "</body>\n" +
           "</html>",
           "<div class='definition'><pre>body</pre></div><div class='content'>Document body.</div>",
           Collections.singletonList("https://developer.mozilla.org/docs/Web/HTML/Element/body"));
  }

  public void testQuickDocumentationHtml5TagDialog() {
    doTest("<!DOCTYPE html>\n" +
           "<html>\n" +
           "<body onload=\"\">\n" +
           "<dia<caret>log></dialog\n" +
           "</body>\n" +
           "</html>",
           "<div class='definition'><pre>dialog</pre></div><div class='content'>Dialog box or window.</div><table class='sections'><tr><td valign='top' class='section'><p>Supported by:</td><td valign='top'>Chrome 37, Chrome Android 37, Firefox 53, Opera 24</td></table>",
           Collections.singletonList("https://developer.mozilla.org/docs/Web/HTML/Element/dialog"));
  }

  public void testQuickDocumentationHtml5Attr() {
    doTest("<!DOCTYPE html>\n" +
           "<html>\n" +
           "<body on<caret>load=\"\">\n" +
           "</body>\n" +
           "</html>",
           "<div class='definition'><pre>onload</pre></div><div class='content'>The document has been loaded.</div>",
           Collections.singletonList("https://developer.mozilla.org/docs/Web/HTML/Element/body#attr-onload"));
  }

  public void testQuickDocumentationHtml5Svg() {
    doTest("<!DOCTYPE html>\n" +
           "<html>\n" +
           "<body>\n" +
           "<sv<caret>g>\n" +
           "</svg>\n" +
           "</body>\n" +
           "</html>",
           "<div class='definition'><pre>svg</pre></div><div class='content'>.</div><table class='sections'><tr><td valign='top' class='section'><p>Supported by:</td><td valign='top'>Chrome, Chrome Android, Edge, Firefox 1.5, IE 9, Opera 8, Safari 3, Safari iOS 3</td></table>",
           Collections.singletonList("https://developer.mozilla.org/docs/Web/SVG/Element/svg"));
  }

  public void testQuickDocumentationHtml5SvgImage() {
    doTest("<!DOCTYPE html>\n" +
           "<html>\n" +
           "<body>\n" +
           "<svg>\n" +
           "<ima<caret>ge>\n" +
           "</image>\n" +
           "</svg>\n" +
           "</body>\n" +
           "</html>",
           "<div class='definition'><pre>image</pre></div><div class='content'>.</div><table class='sections'><tr><td valign='top' class='section'><p>Supported by:</td><td valign='top'>Chrome, Chrome Android, Edge, Firefox 1.5, IE 9, Opera 8, Safari 3.1, Safari iOS 3.1</td></table>",
           Collections.singletonList("https://developer.mozilla.org/docs/Web/SVG/Element/image"));
  }

  public void testQuickDocumentationHtml5Math() {
    doTest("<!DOCTYPE html>\n" +
           "<html>\n" +
           "<body>\n" +
           "<ma<caret>th>\n" +
           "</math>\n" +
           "</body>\n" +
           "</html>",
           null,
           Collections.singletonList("https://developer.mozilla.org/docs/Web/MathML/Element/math"));
  }

  public void testQuickDocumentationHtml5MathMrow() {
    doTest("<!DOCTYPE html>\n" +
           "<html>\n" +
           "<body>\n" +
           "<math>\n" +
           "<mr<caret>ow>\n" +
           "</mrow>\n" +
           "</math>\n" +
           "</body>\n" +
           "</html>",
           "<div class='definition'><pre>mrow</pre></div><div class='content'>Used to add operator before last operand in elementary math notations such as 2D addition, subtraction and multiplication.</div><table class='sections'><tr><td valign='top' class='section'><p>Supported by:</td><td valign='top'>Firefox, Safari 6</td></table>",
           Collections.singletonList("https://developer.mozilla.org/docs/Web/MathML/Element/mrow"));
  }

  public void testQuickDocumentationHtml4Tag() {
    doTest("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01 Transitional//EN\"\n" +
           "   \"http://www.w3.org/TR/html4/loose.dtd\">\n" +
           "<html>\n" +
           "<bo<caret>dy onload=\"\">\n" +
           "</body>\n" +
           "</html>",
           "Tag name:&nbsp;<b>body</b><br>Description  :&nbsp;=================== Document Body ====================================",
           Collections.singletonList("https://developer.mozilla.org/docs/Web/HTML/Element/body"));
  }

  public void testQuickDocumentationHtml4Attr() {
    doTest("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01 Transitional//EN\"\n" +
           "   \"http://www.w3.org/TR/html4/loose.dtd\">\n" +
           "<html>\n" +
           "<body on<caret>load=\"\">\n" +
           "</body>\n" +
           "</html>",
           "<div class='definition'><pre>onload</pre></div><div class='content'>The document has been loaded.</div>",
           Collections.singletonList("https://developer.mozilla.org/docs/Web/HTML/Element/body#attr-onload"));
  }

  public void testQuickDocumentationHtml5Script() {
    doTest("<scr<caret>ipt></script>",
           "<div class='definition'><pre>script</pre></div><div class='content'>Script statements.</div>",
           Collections.singletonList("https://developer.mozilla.org/docs/Web/HTML/Element/script"));
  }

  private void doTest(String text, String doc, List<String> url) {
    configureFromFileText("test.html", text);
    PsiElement originalElement = getFile().findElementAt(getEditor().getCaretModel().getOffset());
    PsiElement element = DocumentationManager.getInstance(getProject()).findTargetElement(getEditor(), getFile());
    DocumentationProvider documentationProvider = DocumentationManager.getProviderFromElement(originalElement);

    assertEquals(doc, documentationProvider.generateDoc(element, originalElement));
    assertEquals(url, documentationProvider.getUrlFor(element, originalElement));
  }
}
