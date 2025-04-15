// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.template;

import com.intellij.lang.Language;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.fixtures.PyTestCase;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Tests for HTML language injection in Python template strings (t-strings) and f-strings.
 */
public class PyHtmlTemplateInjectionTest extends PyTestCase {

  public void testSimpleHtmlInjection() {
    doTestHtmlInjected("x = t'<caret><html><body><h1>Hello, World!</h1></body></html>'");
  }

  public void testSimpleMultiLineHtmlInjection() {
    doTestHtmlInjected("""
                      html_content = t'''
                      <caret><html>
                      <head>
                          <title>Test HTML Injection</title>
                      </head>
                      <body>
                          <h1>Hello, World!</h1>
                      </body>
                      </html>
                      '''
                      """);
  }

  public void testHtmlWithInterpolation() {
    doTestHtmlInjected("""
                         name = "John"
                         x = t'<div><h2>Welcome,<caret> {name}!</h2></div>'
                         """);
  }

  public void testSingleQuotedTString() {
    doTestHtmlInjected("x = t'<caret><span>This is a single-quoted t-string</span>'");
  }

  public void testRawTString() {
    doTestHtmlInjected("x = tr'<caret><div class=\"raw\">Raw t-string with HTML</div>'");
  }

  public void testNoInjectionInRegularString() {
    doTestNoInjection("x = \"<html><body><h1>Hello, World!</h1></body></html>\"");
  }

  public void testNoInjectionInFString() {
    doTestNoInjection("x = f'<caret><span>This is an f-string</span>'");
  }

  public void testNoInjectionInNonHtmlTString() {
    doTestNoInjection("x = t'<caret>plain string, nothing to see here'");
  }

  public void testNoInjectionInNonHtmlTStringWithInterpolation() {
    doTestNoInjection("""
                            name = "John"
                            x = t'<caret>Hi, {name}!'
                            """);
  }

  public void testNoInjectionInEmptyTString() {
    doTestNoInjection("x = t'<caret>'");
  }

  public void testNoInjectionInJsonTString() {
    doTestNoInjection("x = t'<caret>{\"name\": \"John\", \"age\": 30}'");
  }

  public void testHtmlInjectedByScriptTag() {
    doTestHtmlInjected("""
                         evil = t"<caret><script>alert('evil')</script>"
                         """);
  }

  public void testHtmlInjectedByImgTagWithAttributes() {
    doTestHtmlInjected("x = t'<caret><img src=\"shrubbery.jpg\" alt=\"looks nice\" />'");
  }


  public void testHtmlInjectedByImgTagWithInterpolation() {
    doTestHtmlInjected("""
                         attributes = {"src": "shrubbery.jpg", "alt": "looks nice"}
                         template = t"<caret><img {attributes} />"
                         """);
  }

  private void doTestNoInjection(@NotNull String text) {
    myFixture.configureByText(PythonFileType.INSTANCE, text);
    InjectedLanguageManager languageManager = InjectedLanguageManager.getInstance(myFixture.getProject());
    PsiLanguageInjectionHost host = languageManager.getInjectionHost(getElementAtCaret());
    assertNull(host);
  }

  private void doTestHtmlInjected(@NotNull String text) {
    myFixture.configureByText(PythonFileType.INSTANCE, text);
    InjectedLanguageManager languageManager = InjectedLanguageManager.getInstance(myFixture.getProject());
    PsiElement elementAtCaret = getElementAtCaret();
    assertNotNull("Element under caret not found", elementAtCaret);

    PsiLanguageInjectionHost host = languageManager.getInjectionHost(elementAtCaret);
    assertNotNull("Injection host is null", host);

    List<Pair<PsiElement, TextRange>> injectedPsiFiles = languageManager.getInjectedPsiFiles(host);
    assertNotNull("No injected PSI files found", injectedPsiFiles);

    assertFalse("No injected elements found", injectedPsiFiles.isEmpty());
    for (Pair<PsiElement, TextRange> pair : injectedPsiFiles) {
      assertEquals(pair.first.getLanguage(), Language.findLanguageByID("HTML"));
    }
  }
}
