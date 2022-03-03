// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.injection;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.intellij.plugins.markdown.lang.MarkdownFileType;
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownCodeFence;

public class  MarkdownManipulatorTest extends BasePlatformTestCase {
  public void testSimpleCodeFence() {
    doTest("```text\n" +
           "Runti<caret>me\n" +
           "```",
           "singleton_class",
           "```text\n" +
           "singleton_class\n" +
           "```");
  }

  public void testSimpleCodeFenceNewLineBefore() {
    doTest("```text\n" +
           "Runti<caret>me\n" +
           "```",
           "\nRuntime",
           "```text\n" +
           "\n" +
           "Runtime\n" +
           "```");
  }

  public void testSimpleCodeFenceNewLineAfter() {
    doTest("```text\n" +
           "Runt<caret>ime\n" +
           "```",
           "Runtime\n",
           "```text\n" +
           "Runtime\n" +
           "\n" +
           "```");
  }

  public void testCodeFenceInList() {
    doTest("* ```text\n" +
           "  <caret>singleton_class\n" +
           "  ```",
           "singleton",
           "```text\n" +
           "  singleton\n" +
           "  ```");
  }

  public void testCodeFenceInListNewLineBefore() {
    doTest("* ```text\n" +
           "  <caret>singleton_class\n" +
           "  ```",
           "\nsingleton_class",
           "```text\n" +
           "  \n" +
           "  singleton_class\n" +
           "  ```");
  }

  public void testCodeFenceInListNewLineAfter() {
    doTest("* ```text\n" +
           "  <caret>singleton_class\n" +
           "  ```",
           "singleton_class\n",
           "```text\n" +
           "  singleton_class\n" +
           "  \n" +
           "  ```");
  }

  public void testCodeFenceInNestedList() {
    doTest(
      "* A\n" +
           "  * ```text\n" +
           "    <caret>setup\n" +
           "    ```\n" +
           "  * C\n" +
           "*D",
           "singleton_class\n",
           "```text\n" +
           "    singleton_class\n" +
           "    \n" +
           "    ```");
  }

  public void testCodeFenceInQuotes() {
    doTest(">```text\n" +
           "><caret>setup\n" +
           ">```",
           "singleton_class\n",
           "```text\n" +
           ">singleton_class\n" +
           ">\n" +
           ">```");
  }

  public void testInQuotesInListComplex() {
    doTest("* C\n" +
           "  * B \n" +
           "    >  * A \n" +
           "    >    \n" +
           "    >  * D  \n" +
           "  -  >  -    > ```text\n" +
           "     >       > <caret>$LAST_MATCH_INFO\n" +
           "     >       > ```\n",
           "singleton_class",
           "```text\n" +
           "     >       > singleton_class\n" +
           "     >       > ```");
  }

  public void testInQuotesInListNewLineBefore() {
    doTest("* C\n" +
           "  * B \n" +
           "    >  * A \n" +
           "    >    \n" +
           "    >  * D  \n" +
           "  -  >  -    > ```text\n" +
           "     >       > <caret>$LAST_MATCH_INFO\n" +
           "     >       > ```\n",
           "\nsingleton_class",
           "```text\n" +
           "     >       > \n" +
           "     >       > singleton_class\n" +
           "     >       > ```");
  }

  public void testInQuotesInListNewLineAfter() {
    doTest("* C\n" +
           "  * B \n" +
           "    >  * A \n" +
           "    >    \n" +
           "    >  * D  \n" +
           "  -  >  -    > ```text\n" +
           "     >       > <caret>$LAST_MATCH_INFO\n" +
           "     >       > ```\n",
           "singleton_class\n",
           "```text\n" +
           "     >       > singleton_class\n" +
           "     >       > \n" +
           "     >       > ```");
  }

  public void testThreeBackticksCodeFence() {
    doTest("```text\n" +
           "Runti<caret>me\n" +
           "```",
           "```singleton_class",
           "```singleton_class");
  }

  public void testThreeTildaCodeFence() {
    doTest("```text\n" +
           "Runti<caret>me\n" +
           "```",
           "~~~singleton_class",
           "~~~singleton_class");
  }

  public void doTest(String text, String newContent, String expectedText) {
    myFixture.configureByText(MarkdownFileType.INSTANCE, text);

    int offset = myFixture.getCaretOffset();

    PsiElement element = myFixture.getFile().findElementAt(offset);
    MarkdownCodeFence codeFence = (MarkdownCodeFence)InjectedLanguageManager.getInstance(getProject()).getInjectionHost(element);

    final MarkdownCodeFence.Manipulator manipulator = new MarkdownCodeFence.Manipulator();
    MarkdownCodeFence newCodeFence =
      WriteCommandAction.runWriteCommandAction(myFixture.getProject(), (Computable<MarkdownCodeFence>)() -> manipulator
        .handleContentChange(codeFence, TextRange.from(element.getTextOffset(), element.getTextLength()), newContent));

    assertEquals(expectedText, newCodeFence != null ? newCodeFence.getText() : codeFence.getText());
  }
}