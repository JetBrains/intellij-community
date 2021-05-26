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
import junit.framework.ComparisonFailure;

import java.util.Collections;
import java.util.List;

public class HtmlDocumentationTest extends LightPlatformCodeInsightTestCase {
  public void testQuickDocumentationHtml5Tag() {
    doTest("<!DOCTYPE html>\n" +
           "<html>\n" +
           "<bo<caret>dy onload=\"\">\n" +
           "</body>\n" +
           "</html>",
           "<div class='definition'><pre>body</pre></div><div class='content'>" +
           "<p><span class=\"seoSummary\">The <strong>HTML <code>&lt;body&gt;</code> Element</strong> represents the content of",
           Collections.singletonList("https://developer.mozilla.org/en-us/docs/web/html/element/body"));
  }

  public void testQuickDocumentationHtml5TagDialog() {
    doTest("<!DOCTYPE html>\n" +
           "<html>\n" +
           "<body onload=\"\">\n" +
           "<dia<caret>log></dialog\n" +
           "</body>\n" +
           "</html>",
           "<div class='definition'><pre>dialog</pre></div><div class='content'>" +
           "<p><span class=\"seoSummary\">The <strong>HTML <code>&lt;dialog&gt;</code> element</strong> represents a dialog box",
           Collections.singletonList("https://developer.mozilla.org/en-us/docs/web/html/element/dialog"));
  }

  public void testQuickDocumentationHtml5Attr() {
    doTest("<!DOCTYPE html>\n" +
           "<html>\n" +
           "<body on<caret>load=\"\">\n" +
           "</body>\n" +
           "</html>",
           "<div class='definition'><pre>onload</pre></div><div class='content'>Function to call when the document has finished loading.",
           Collections.singletonList("https://developer.mozilla.org/en-us/docs/web/html/element/body#attr-onload"));
  }

  public void testQuickDocumentationHtml5Svg() {
    doTest("<!DOCTYPE html>\n" +
           "<html>\n" +
           "<body>\n" +
           "<sv<caret>g>\n" +
           "</svg>\n" +
           "</body>\n" +
           "</html>",
           "<div class='definition'><pre>svg</pre></div><div class='content'>" +
           "<p>The <code>svg</code> element is a container that defines a new coordinate system and",
           Collections.singletonList("https://developer.mozilla.org/en-us/docs/web/svg/element/svg"));
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
           "<div class='definition'><pre>image</pre></div><div class='content'>" +
           "<p><span class=\"seoSummary\">The <strong><code>&lt;image&gt;</code></strong> SVG element includes images inside SVG documents.",
           Collections.singletonList("https://developer.mozilla.org/en-us/docs/web/svg/element/image"));
  }

  public void testQuickDocumentationHtml5Math() {
    doTest("<!DOCTYPE html>\n" +
           "<html>\n" +
           "<body>\n" +
           "<ma<caret>th>\n" +
           "</math>\n" +
           "</body>\n" +
           "</html>",
           "<div class='definition'><pre>math</pre></div><div class='content'>" +
           "<p class=\"summary\">The top-level element in MathML is <code>&lt;math&gt;</code>.",
           Collections.singletonList("https://developer.mozilla.org/en-us/docs/web/mathml/element/math"));
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
           "<div class='definition'><pre>mrow</pre></div><div class='content'>" +
           "<p class=\"summary\">The MathML <code>&lt;mrow&gt;</code> element is used to group sub-expressions",
           Collections.singletonList("https://developer.mozilla.org/en-us/docs/web/mathml/element/mrow"));
  }

  public void testQuickDocumentationHtml4Tag() {
    doTest("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01 Transitional//EN\"\n" +
           "   \"http://www.w3.org/TR/html4/loose.dtd\">\n" +
           "<html>\n" +
           "<bo<caret>dy onload=\"\">\n" +
           "</body>\n" +
           "</html>",
           "<div class='definition'><pre>body</pre></div><div class='content'><p><span class=\"seoSummary\">The <strong>HTML <code>&lt;body&gt;</code> Element</strong> represents the content of an HTML document. ",
           Collections.singletonList("https://developer.mozilla.org/en-us/docs/web/html/element/body"));
  }

  public void testQuickDocumentationHtml4Attr() {
    doTest("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01 Transitional//EN\"\n" +
           "   \"http://www.w3.org/TR/html4/loose.dtd\">\n" +
           "<html>\n" +
           "<body on<caret>load=\"\">\n" +
           "</body>\n" +
           "</html>",
           "<div class='definition'><pre>onload</pre></div><div class='content'>Function to call when the document has finished loading.",
           Collections.singletonList("https://developer.mozilla.org/en-us/docs/web/html/element/body#attr-onload"));
  }

  public void testQuickDocumentationHtml5Script() {
    doTest("<scr<caret>ipt></script>",
           "<div class='definition'><pre>script</pre></div><div class='content'>" +
           "<p><span class=\"seoSummary\">The <strong>HTML <code>&lt;script&gt;</code> element</strong> is used to embed executable",
           Collections.singletonList("https://developer.mozilla.org/en-us/docs/web/html/element/script"));
  }


  public void testQuickDocumentationHtml5MediaEvents() {
    doTest("<video on<caret>stalled=''>",
           "<div class='definition'><pre>onstalled</pre></div><div class='content'><p><span class=\"seoSummary\">" +
           "The <code>stalled</code> event is fired when the user agent is trying to fetch media data",
           Collections.singletonList("https://developer.mozilla.org/en-us/docs/web/api/htmlmediaelement/stalled_event"));
  }

  private void doTest(String text, String doc, List<String> url) {
    configureFromFileText("test.html", text);
    PsiElement originalElement = getFile().findElementAt(getEditor().getCaretModel().getOffset());
    PsiElement element = DocumentationManager.getInstance(getProject()).findTargetElement(getEditor(), getFile());
    DocumentationProvider documentationProvider = DocumentationManager.getProviderFromElement(originalElement);

    String generatedDoc = documentationProvider.generateDoc(element, originalElement);
    if (generatedDoc == null) {
      //noinspection ConstantConditions
      assertEquals(doc, generatedDoc);
    } else if (doc == null || !generatedDoc.startsWith(doc)) {
      throw new ComparisonFailure("Generated doc doesn't start with correct prefix", doc, generatedDoc);
    }
    assertEquals(url, documentationProvider.getUrlFor(element, originalElement));
  }
}
