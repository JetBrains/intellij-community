// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;
import org.intellij.plugins.markdown.injection.LanguageGuesser;
import org.intellij.plugins.markdown.lang.MarkdownFileType;
import org.intellij.plugins.markdown.lang.MarkdownLanguage;
import org.intellij.plugins.markdown.settings.MarkdownApplicationSettings;

import java.util.Arrays;
import java.util.stream.Collectors;

public class MarkdownInjectionTest extends LightPlatformCodeInsightFixtureTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
  }

  public void testFenceWithLang() {
    doTest("```text\n" +
           "{\"foo\":\n" +
           "  <caret>\n" +
           "  bar\n" +
           "}\n" +
           "```", true);
  }

  public void testFenceDoesNotIgnoreLineSeparators() {
    final String content = "class C {\n" +
                           "\n" +
                           "public static void ma<caret>in(String[] args) {\n" +
                           "  \n" +
                           "}\n" +
                           "\n" +
                           "}";
    final String text = "```text\n" +
                        content + "\n" +
                        "```";
    doTest(text, true);
    final PsiElement element = myFixture.getFile().findElementAt(myFixture.getCaretOffset());
    assertEquals(content.replace("<caret>", ""), element.getContainingFile().getText());
  }

  public void testFenceInQuotes() {
    final String content = "class C {\n" +
                           "\n" +
                           "public static void ma<caret>in(String[] args) {\n" +
                           "  \n" +
                           "}\n" +
                           "\n" +
                           "\n" +
                           "\n" +
                           "}";
    final String text = "> ```text\n" +
                        Arrays.stream(content.split("\\n")).map(s -> "> " + s).collect(Collectors.joining("\n")) + "\n" +
                        "> ```";

    doTest(text, true);
    final PsiElement element = myFixture.getFile().findElementAt(myFixture.getCaretOffset());
    assertEquals(content.replace("<caret>", ""), element.getContainingFile().getText());
  }

  public void testFenceWithLangWithDisabledAutoInjection() {
    MarkdownApplicationSettings markdownSettings = MarkdownApplicationSettings.getInstance();
    boolean oldValue = markdownSettings.isDisableInjections();
    try {
      markdownSettings.setDisableInjections(true);
      doTest("```text\n" +
             "{\"foo\":\n" +
             "  <caret>\n" +
             "  bar\n" +
             "}\n" +
             "```", false);
    }
    finally {
      markdownSettings.setDisableInjections(oldValue);
    }
  }

  public void testFenceWithHtml() {
    assertNotNull(LanguageGuesser.INSTANCE.guessLanguage("text"));
  }

  private void doTest(String text, boolean shouldHaveInjection) {
    final PsiFile file = myFixture.configureByText(MarkdownFileType.INSTANCE, text);
    assertEquals(shouldHaveInjection, !file.findElementAt(myFixture.getCaretOffset()).getLanguage().isKindOf(MarkdownLanguage.INSTANCE));
  }
}
