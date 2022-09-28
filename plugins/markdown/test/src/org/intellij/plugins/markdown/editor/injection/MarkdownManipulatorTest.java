// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.injection;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.intellij.plugins.markdown.lang.MarkdownFileType;
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownCodeFence;

public class  MarkdownManipulatorTest extends BasePlatformTestCase {
  public void testSimpleCodeFence() {
    doTest("""
             ```text
             Runti<caret>me
             ```""",
           "singleton_class",
           """
             ```text
             singleton_class
             ```""");
  }

  public void testSimpleCodeFenceNewLineBefore() {
    doTest("""
             ```text
             Runti<caret>me
             ```""",
           "\nRuntime",
           """
             ```text

             Runtime
             ```""");
  }

  public void testSimpleCodeFenceNewLineAfter() {
    doTest("""
             ```text
             Runt<caret>ime
             ```""",
           "Runtime\n",
           """
             ```text
             Runtime

             ```""");
  }

  public void testCodeFenceInList() {
    doTest("""
             * ```text
               <caret>singleton_class
               ```""",
           "singleton",
           """
             ```text
               singleton
               ```""");
  }

  public void testCodeFenceInListNewLineBefore() {
    doTest("""
             * ```text
               <caret>singleton_class
               ```""",
           "\nsingleton_class",
           """
             ```text
              \s
               singleton_class
               ```""");
  }

  public void testCodeFenceInListNewLineAfter() {
    doTest("""
             * ```text
               <caret>singleton_class
               ```""",
           "singleton_class\n",
           """
             ```text
               singleton_class
              \s
               ```""");
  }

  public void testCodeFenceInNestedList() {
    doTest(
      """
        * A
          * ```text
            <caret>setup
            ```
          * C
        *D""",
           "singleton_class\n",
      """
        ```text
            singleton_class
           \s
            ```""");
  }

  public void testCodeFenceInQuotes() {
    doTest("""
             >```text
             ><caret>setup
             >```""",
           "singleton_class\n",
           """
             ```text
             >singleton_class
             >
             >```""");
  }

  public void testInQuotesInListComplex() {
    doTest("""
             * C
               * B\s
                 >  * A\s
                 >   \s
                 >  * D \s
               -  >  -    > ```text
                  >       > <caret>$LAST_MATCH_INFO
                  >       > ```
             """,
           "singleton_class",
           """
             ```text
                  >       > singleton_class
                  >       > ```""");
  }

  public void testInQuotesInListNewLineBefore() {
    doTest("""
             * C
               * B\s
                 >  * A\s
                 >   \s
                 >  * D \s
               -  >  -    > ```text
                  >       > <caret>$LAST_MATCH_INFO
                  >       > ```
             """,
           "\nsingleton_class",
           """
             ```text
                  >       >\s
                  >       > singleton_class
                  >       > ```""");
  }

  public void testInQuotesInListNewLineAfter() {
    doTest("""
             * C
               * B\s
                 >  * A\s
                 >   \s
                 >  * D \s
               -  >  -    > ```text
                  >       > <caret>$LAST_MATCH_INFO
                  >       > ```
             """,
           "singleton_class\n",
           """
             ```text
                  >       > singleton_class
                  >       >\s
                  >       > ```""");
  }

  public void testThreeBackticksCodeFence() {
    doTest("""
             ```text
             Runti<caret>me
             ```""",
           "```singleton_class",
           "```singleton_class");
  }

  public void testThreeTildaCodeFence() {
    doTest("""
             ```text
             Runti<caret>me
             ```""",
           "~~~singleton_class",
           "~~~singleton_class");
  }

  public void doTest(String text, String newContent, String expectedText) {
    myFixture.configureByText(MarkdownFileType.INSTANCE, text);

    int offset = myFixture.getCaretOffset();

    PsiElement element = myFixture.getFile().findElementAt(offset);
    MarkdownCodeFence codeFence = (MarkdownCodeFence)InjectedLanguageManager.getInstance(getProject()).getInjectionHost(element);
    assertNotNull("Failed to find fence element", codeFence);
    final var manipulator = ElementManipulators.getNotNullManipulator(codeFence);
    final var newCodeFence = WriteCommandAction.runWriteCommandAction(myFixture.getProject(), (Computable<MarkdownCodeFence>)() -> {
      return manipulator.handleContentChange(codeFence, TextRange.from(element.getTextOffset(), element.getTextLength()), newContent);
    });

    assertEquals(expectedText, newCodeFence != null ? newCodeFence.getText() : codeFence.getText());
  }
}