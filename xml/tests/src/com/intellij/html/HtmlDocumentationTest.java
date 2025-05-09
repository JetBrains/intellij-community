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
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import junit.framework.ComparisonFailure;

import java.util.Collections;
import java.util.List;

public class HtmlDocumentationTest extends BasePlatformTestCase {
  public void testQuickDocumentationHtml5Tag() {
    doTest("""
             <!DOCTYPE html>
             <html>
             <bo<caret>dy onload="">
             </body>
             </html>""",
           "<div class='definition'><pre>body</pre></div>\n<div class='content'>" +
           "The <strong><code>&lt;body&gt;</code></strong> <a href=\"https://developer.mozilla.org/en-us/docs/Web/HTML\">HTML</a> " +
           "element represents the content of",
           Collections.singletonList("https://developer.mozilla.org/en-us/docs/web/html/reference/elements/body"));
  }

  public void testQuickDocumentationHtml5TagDialog() {
    doTest("""
             <!DOCTYPE html>
             <html>
             <body onload="">
             <dia<caret>log></dialog
             </body>
             </html>""",
           "<div class='definition'><pre>dialog</pre></div>\n<div class='content'>" +
           "The <strong><code>&lt;dialog&gt;</code></strong> <a href=\"https://developer.mozilla.org/en-us/docs/Web/HTML\">HTML</a> " +
           "element represents a modal",
           Collections.singletonList("https://developer.mozilla.org/en-us/docs/web/html/reference/elements/dialog"));
  }

  public void testQuickDocumentationHtml5Attr() {
    doTest("""
             <!DOCTYPE html>
             <html>
             <body on<caret>load="">
             </body>
             </html>""",
           "<div class='definition'><pre>onload</pre></div>\n<div class='content'>Function to call when the document has finished loading.",
           Collections.singletonList("https://developer.mozilla.org/en-us/docs/web/html/element/body#onload"));
  }

  public void testQuickDocumentationHtml5Svg() {
    doTest("""
             <!DOCTYPE html>
             <html>
             <body>
             <sv<caret>g>
             </svg>
             </body>
             </html>""",
           "<div class='definition'><pre>svg</pre></div>\n<div class='content'>" +
           "The <strong><code>&lt;svg&gt;</code></strong> <a href=\"https://developer.mozilla.org/en-us/docs/Web/SVG\">SVG</a> element is a container that defines a new coordinate system and",
           Collections.singletonList("https://developer.mozilla.org/en-us/docs/web/svg/reference/element/svg"));
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
             </html>""",
           "<div class='definition'><pre>image</pre></div>\n<div class='content'>" +
           "The <strong><code>&lt;image&gt;</code></strong> <a href=\"https://developer.mozilla.org/en-us/docs/Web/SVG\">SVG</a> element includes images inside SVG documents.",
           Collections.singletonList("https://developer.mozilla.org/en-us/docs/web/svg/reference/element/image"));
  }

  public void testQuickDocumentationHtml5Math() {
    doTest("""
             <!DOCTYPE html>
             <html>
             <body>
             <ma<caret>th>
             </math>
             </body>
             </html>""",
           "<div class='definition'><pre>math</pre></div>\n<div class='content'>" +
           "The <strong><code>&lt;math&gt;</code></strong> <a href=\"https://developer.mozilla.org/en-us/docs/Web/MathML\">MathML</a> " +
           "element is the top-level MathML element",
           Collections.singletonList("https://developer.mozilla.org/en-us/docs/web/mathml/reference/element/math"));
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
             </html>""",
           "<div class='definition'><pre>mrow</pre></div>\n<div class='content'>" +
           "The <strong><code>&lt;mrow&gt;</code></strong> <a href=\"https://developer.mozilla.org/en-us/docs/Web/MathML\">MathML</a> " +
           "element is used to group sub-expressions",
           Collections.singletonList("https://developer.mozilla.org/en-us/docs/web/mathml/reference/element/mrow"));
  }

  public void testQuickDocumentationHtml4Tag() {
    doTest("""
             <!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN"
                "http://www.w3.org/TR/html4/loose.dtd">
             <html>
             <bo<caret>dy onload="">
             </body>
             </html>""",
           "<div class='definition'><pre>body</pre></div>\n<div class='content'>" +
           "The <strong><code>&lt;body&gt;</code></strong> <a href=\"https://developer.mozilla.org/en-us/docs/Web/HTML\">HTML</a> " +
           "element represents the content of an HTML document.",
           Collections.singletonList("https://developer.mozilla.org/en-us/docs/web/html/reference/elements/body"));
  }

  public void testQuickDocumentationHtml4Attr() {
    doTest("""
             <!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN"
                "http://www.w3.org/TR/html4/loose.dtd">
             <html>
             <body on<caret>load="">
             </body>
             </html>""",
           "<div class='definition'><pre>onload</pre></div>\n<div class='content'>Function to call when the document has finished loading.",
           Collections.singletonList("https://developer.mozilla.org/en-us/docs/web/html/element/body#onload"));
  }

  public void testQuickDocumentationHtml5Script() {
    doTest("<scr<caret>ipt></script>",
           "<div class='definition'><pre>script</pre></div>\n<div class='content'>" +
           "The <strong><code>&lt;script&gt;</code></strong> <a href=\"https://developer.mozilla.org/en-us/docs/Web/HTML\">HTML</a> " +
           "element is used to embed executable",
           Collections.singletonList("https://developer.mozilla.org/en-us/docs/web/html/reference/elements/script"));
  }


  public void testQuickDocumentationHtml5MediaEvents() {
    doTest("<video on<caret>stalled=''>",
           "<div class='definition'><pre>onstalled</pre></div>\n<div class='content'>" +
           "The <code>stalled</code> event is fired when the user agent is trying to fetch media data",
           Collections.singletonList("https://developer.mozilla.org/en-us/docs/web/api/htmlmediaelement/stalled_event"));
  }

  public void testLookupDocWordCompletions() {
    myFixture.configureByText("test.html", "<html lang='en'>la<caret>n");
    PsiElement originalElement = myFixture.getFile().findElementAt(myFixture.getEditor().getCaretModel().getOffset());
    DocumentationProvider documentationProvider = DocumentationManager.getProviderFromElement(originalElement);
    PsiElement element = documentationProvider.getDocumentationElementForLookupItem(originalElement.getManager(), "lang", originalElement);
    assertNull(element);
  }

  private void doTest(String text, String doc, List<String> url) {
    myFixture.configureByText("test.html", text);
    PsiElement originalElement = myFixture.getFile().findElementAt(myFixture.getEditor().getCaretModel().getOffset());
    PsiElement element = DocumentationManager.getInstance(getProject()).findTargetElement(myFixture.getEditor(), myFixture.getFile());
    DocumentationProvider documentationProvider = DocumentationManager.getProviderFromElement(originalElement);

    String generatedDoc = documentationProvider.generateDoc(element, originalElement);
    if (generatedDoc != null) {
      generatedDoc = generatedDoc.replaceAll("(?s)<details>.+</details>\n", "");
    }
    if (generatedDoc == null) {
      //noinspection ConstantConditions
      assertEquals(doc, generatedDoc);
    } else if (doc == null || !generatedDoc.startsWith(doc)) {
      throw new ComparisonFailure("Generated doc doesn't start with correct prefix", doc, generatedDoc);
    }
    assertEquals(url, documentationProvider.getUrlFor(element, originalElement));
  }
}
