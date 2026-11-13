// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.todo;

import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.ide.todo.TodoConfiguration;
import com.intellij.ide.todo.nodes.TodoFileNode;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.PsiTodoSearchHelper;
import com.intellij.psi.search.TodoAttributesUtil;
import com.intellij.psi.search.TodoPattern;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.intellij.plugins.markdown.lang.MarkdownFileType;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;

@TestApplication
public class MarkdownTodoTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);
  private final TestFixture<CodeInsightTestFixture> codeInsightFixture = codeInsightModuleFixture(projectFixture);

  @Test
  public void testTodo() {
    doTest("[//]: #a (todo comment)", 1);
  }

  @Test
  public void testTodoNoLink() {
    doTest("[//]: # (todo comment)", 1);
  }

  @Test
  public void testHtmlTodo() {
    doTest("<!-- todo comment -->", 1);
  }

  @Test
  public void testTodoTodoPosition() {
    doTest("[//]: #a (todo todo comment)", 1);
  }

  @Test
  public void testTodoStartPosition() {
    doTest("[//]: #a (a todo comment)", 1);
  }

  @Test
  public void testLinkNotTodo() {
    doTest("[url](todo comment)", 0);
  }

  @Test
  public void testLinkReferenceDefinition() {
    doTest("[foo]: /url \"todo comment\"", 0);
  }

  @Test
  public void testTodoInNotALinkPosition() {
    doTest("todo comment", 0);
  }

  @Test
  public void testTodoInHtmlComment() {
    doTest("<!-- todo comment -->", 1);
  }

  @Test
  public void testTodoInsideHtmlBlock() {
    doTest("<div>\n  <!-- todo inside html block -->\n</div>", 1);
  }

  @Test
  public void testNoTodoInsideHtmlText() {
    doTest("<div>todo not in comment</div>", 0);
  }

  @Test
  public void testNoTodoInsideHtmlCommentInCodeFence() {
    doFullTest("```markdown\n<!-- todo inside fenced code block -->\n```", 0);
  }

  @Test
  public void testNoTodoInsideHtmlCodeFence() {
    String text = """
      ```html
      <!-- todo inside html code fence should not be indexed -->
      ```

      [//]: # (supported markdown link todo comment)

      <!-- supported html comment fixme -->""";
    doFullTest(text, 2);
  }

  @Test
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

  @Test
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
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText(MarkdownFileType.INSTANCE, text);
    PsiFile file = myFixture.getFile();
    PsiTodoSearchHelper helper = PsiTodoSearchHelper.getInstance(myFixture.getProject());
    int actualTodos = ReadAction.computeBlocking(() -> helper.findTodoItems(file).length);
    assertEquals(expectedTodos, actualTodos);
  }

  private void doFullTest(@NotNull String text, int expectedTodos) {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText(MarkdownFileType.INSTANCE, text);
    PsiFile file = myFixture.getFile();
    PsiTodoSearchHelper helper = PsiTodoSearchHelper.getInstance(myFixture.getProject());
    ReadAction.run(() -> {
      assertEquals(expectedTodos, helper.findTodoItems(file).length, "findTodoItems");
      assertEquals(expectedTodos, TodoFileNode.findAllTodos(file, helper).size(), "TODO tool window items");
    });
    long editorTodos = myFixture.doHighlighting().stream()
      .filter(info -> info.type == HighlightInfoType.TODO)
      .count();
    assertEquals(expectedTodos, editorTodos, "editor TODO highlights");
  }

  @Test
  public void testCustomTodo() {
    TodoConfiguration todo = TodoConfiguration.getInstance();
    TodoPattern[] oldPatterns = todo.getTodoPatterns();
    try {
      todo.setTodoPatterns(new TodoPattern[]{new TodoPattern("\\btodo comment\\b.*", TodoAttributesUtil.createDefault(), false)});
      EdtTestUtil.runInEdtAndWait(() -> PlatformTestUtil.dispatchAllEventsInIdeEventQueue());
      doTest("[//]: #s (todo comment)", 1);
    }
    finally {
      todo.setTodoPatterns(oldPatterns);
    }
  }
}
