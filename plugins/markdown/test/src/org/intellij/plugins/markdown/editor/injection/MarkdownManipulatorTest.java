// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.injection;

import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.intellij.plugins.markdown.lang.MarkdownFileType;
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownCodeFence;
import org.junit.jupiter.api.Test;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestApplication
public class MarkdownManipulatorTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);
  private final TestFixture<CodeInsightTestFixture> codeInsightFixture = codeInsightModuleFixture(projectFixture);

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
  public void testThreeBackticksCodeFence() {
    doTest("""
             ```text
             Runti<caret>me
             ```""",
           "```singleton_class",
           "```singleton_class");
  }

  @Test
  public void testThreeTildaCodeFence() {
    doTest("""
             ```text
             Runti<caret>me
             ```""",
           "~~~singleton_class",
           "~~~singleton_class");
  }

  public void doTest(String text, String newContent, String expectedText) {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText(MarkdownFileType.INSTANCE, text);

    Pair<PsiElement, MarkdownCodeFence> found = ReadAction.computeBlocking(() -> {
      PsiElement elementAtCaret = myFixture.getFile().findElementAt(myFixture.getCaretOffset());
      return Pair.create(elementAtCaret,
                         (MarkdownCodeFence)InjectedLanguageManager.getInstance(myFixture.getProject()).getInjectionHost(elementAtCaret));
    });
    PsiElement element = found.first;
    MarkdownCodeFence codeFence = found.second;
    assertNotNull(codeFence, "Failed to find fence element");
    final var manipulator = ElementManipulators.getNotNullManipulator(codeFence);
    final var newCodeFence = WriteCommandAction.runWriteCommandAction(myFixture.getProject(), (Computable<MarkdownCodeFence>)() -> {
      return manipulator.handleContentChange(codeFence, TextRange.from(element.getTextOffset(), element.getTextLength()), newContent);
    });

    assertEquals(expectedText, ReadAction.computeBlocking(() -> newCodeFence != null ? newCodeFence.getText() : codeFence.getText()));
  }
}