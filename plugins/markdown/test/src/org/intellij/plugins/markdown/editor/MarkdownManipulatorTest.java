// Copyright 2000-2018 JetBrains s.r.o.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package org.intellij.plugins.markdown.editor;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;
import org.intellij.plugins.markdown.lang.MarkdownFileType;
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownCodeFenceImpl;

public class MarkdownManipulatorTest extends LightPlatformCodeInsightFixtureTestCase {
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
    doTest("* A\n" +
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
    MarkdownCodeFenceImpl codeFence = (MarkdownCodeFenceImpl)InjectedLanguageManager.getInstance(getProject()).getInjectionHost(element);

    final MarkdownCodeFenceImpl.Manipulator manipulator = new MarkdownCodeFenceImpl.Manipulator();
    MarkdownCodeFenceImpl newCodeFence =
      WriteCommandAction.runWriteCommandAction(myFixture.getProject(), (Computable<MarkdownCodeFenceImpl>)() -> manipulator
        .handleContentChange(codeFence, TextRange.from(element.getTextOffset(), element.getTextLength()), newContent));

    assertEquals(expectedText, newCodeFence != null ? newCodeFence.getText() : codeFence.getText());
  }
}