// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.todo;

import com.intellij.ide.todo.TodoConfiguration;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.ide.todo.nodes.TodoFileNode;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.PsiTodoSearchHelper;
import com.intellij.psi.search.TodoAttributesUtil;
import com.intellij.psi.search.TodoPattern;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.intellij.plugins.markdown.lang.MarkdownFileType;
import org.jetbrains.annotations.NotNull;

public class MarkdownTodoTest extends BasePlatformTestCase {
  public void testTodo() {
    doTest("[//]: #a (todo comment)", 1);
  }

  public void testTodoNoLink() {
    doTest("[//]: # (todo comment)", 1);
  }

  public void testTodoTodoPosition() {
    doTest("[//]: #a (todo todo comment)", 1);
  }

  public void testTodoStartPosition() {
    doTest("[//]: #a (a todo comment)", 1);
  }

  public void testLinkNotTodo() {
    doTest("[url](todo comment)", 0);
  }

  public void testLinkReferenceDefinition() {
    doTest("[foo]: /url \"todo comment\"", 0);
  }

  public void testTodoInNotALinkPosition() {
    doTest("todo comment", 0);
  }

  public void testTodoInHtmlComment() {
    doTest("<!-- todo comment -->", 1);
  }

  public void testTodoInsideHtmlBlock() {
    doTest("<div>\n  <!-- todo inside html block -->\n</div>", 1);
  }

  public void testNoTodoInsideHtmlText() {
    doTest("<div>todo not in comment</div>", 0);
  }

  public void testNoTodoInsideHtmlCommentInCodeFence() {
    doFullTest("```markdown\n<!-- todo inside fenced code block -->\n```", 0);
  }

  public void testNoTodoInsideHtmlCodeFence() {
    String text = """
      ```html
      <!-- todo inside html code fence should not be indexed -->
      ```

      [//]: # (supported markdown link todo comment)

      <!-- supported html comment fixme -->""";
    doFullTest(text, 2);
  }

  public void testNoTodoInsideNonMarkdownCodeFence() {
    String text = """
      ```java
      // todo line comment in java fence should not be indexed
      /* todo block comment in java fence should not be indexed */
      ```

      [//]: # (supported markdown link todo comment)

      <!-- supported html comment fixme -->""";
    doFullTest(text, 2);
  }

  public void testTodoInHtmlCommentWithCodeFenceAround() {
    String text = """
      ```markdown
      <!-- todo inside fenced markdown example should not be indexed -->
      [//]: # (todo inside fenced markdown example should not be indexed)
      ```

      [//]: # (supported markdown link todo comment)

      <!-- supported html comment fixme -->""";
    doFullTest(text, 2);
  }

  private void doTest(@NotNull String text, int expectedTodos) {
    myFixture.configureByText(MarkdownFileType.INSTANCE, text);
    assertEquals(expectedTodos, PsiTodoSearchHelper.getInstance(getProject()).findTodoItems(myFixture.getFile()).length);
  }

  private void doFullTest(@NotNull String text, int expectedTodos) {
    myFixture.configureByText(MarkdownFileType.INSTANCE, text);
    PsiFile file = myFixture.getFile();
    PsiTodoSearchHelper helper = PsiTodoSearchHelper.getInstance(getProject());
    assertEquals("findTodoItems", expectedTodos, helper.findTodoItems(file).length);
    assertEquals("TODO tool window items", expectedTodos, TodoFileNode.findAllTodos(file, helper).size());
    long editorTodos = myFixture.doHighlighting().stream()
      .filter(info -> info.type == HighlightInfoType.TODO)
      .count();
    assertEquals("editor TODO highlights", expectedTodos, editorTodos);
  }

  public void testCustomTodo() {
    TodoConfiguration todo = TodoConfiguration.getInstance();
    TodoPattern[] oldPatterns = todo.getTodoPatterns();
    try {
      todo.setTodoPatterns(new TodoPattern[]{new TodoPattern("\\btodo comment\\b.*", TodoAttributesUtil.createDefault(), false)});
      PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
      doTest("[//]: #s (todo comment)", 1);
    }
    finally {
      todo.setTodoPatterns(oldPatterns);
    }
  }
}
