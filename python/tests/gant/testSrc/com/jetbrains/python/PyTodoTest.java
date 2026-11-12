// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.intellij.editor.TodoItemsTestUtil;
import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.ide.todo.TodoConfiguration;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.TodoAttributesUtil;
import com.intellij.psi.search.TodoPattern;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.jetbrains.python.allure.Layers;
import com.jetbrains.python.allure.Subsystems;
import org.junit.jupiter.api.Test;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;

@Subsystems.IDE
@Layers.Functional
@TestApplication
public class PyTodoTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<CodeInsightTestFixture> codeInsightFixture = codeInsightModuleFixture(projectFixture);

  private void testTodos(String text) {
    TodoItemsTestUtil.testTodos(codeInsightFixture.get(), PythonFileType.INSTANCE.getDefaultExtension(), text);
  }

  private void checkTodos(String text) {
    TodoItemsTestUtil.checkTodos(codeInsightFixture.get(), text);
  }

  @Test
  public void testTodo() {
    testTodos("# [TODO: return dead parrot]\n");
  }

  @Test
  public void testCustomTodo() {
    final TodoConfiguration todo = TodoConfiguration.getInstance();
    final TodoPattern[] oldPatterns = todo.getTodoPatterns();
    try {
      TodoItemsTestUtil.setTodoPatterns(new TodoPattern[]{new TodoPattern("\\bDas\\b.*", TodoAttributesUtil.createDefault(), false)});
      testTodos("# [Das Rindfleischetikettierungsueberwachungsaufgabenuebertragungsgesetz]\n");
    }
    finally {
      TodoItemsTestUtil.setTodoPatterns(oldPatterns);
    }
  }

  // PY-11040
  @Test
  public void testTodoInDocstrings() {
    testTodos("""
                ''' [TODO: return dead parrot ]'''
                def foo():
                ''' [TODO: return dead parrot ]'''
                """);
  }

  // PY-11040
  @Test
  public void testTodoInTripleQuotedString() {
    testTodos("s = ''' TODO: return dead parrot '''");
  }

  // PY-6027
  @Test
  public void testSuccessiveLineComments() {
    testTodos("# [TODO first line]\n" +
              "#  [second line]");
  }

  // PY-6027
  @Test
  public void testSuccessiveLineCommentsAfterEditing() {
    testTodos("# [TODO first line]\n" +
              "# <caret>second line");
    codeInsightFixture.get().type("     ");
    checkTodos("# [TODO first line]\n" +
               "#      [second line]");
  }

  // PY-6027
  @Test
  public void testAllLinesLoseHighlightingWithFirstLine() {
    testTodos("# [TO<caret>DO first line]\n" +
              "#  [second line]");
    TodoItemsTestUtil.delete(codeInsightFixture.get());
    checkTodos("# TOO first line\n" +
               "#  second line");
  }

  // PY-6027
  @Test
  public void testContinuationIsNotOverlappedWithFollowingTodo() {
    testTodos("# [TODO first line]\n" +
              "#  [TODO second line]");
  }

  // PY-6027
  @Test
  public void testContinuationInBlockCommentWithStars() {
    testTodos("""
                '''
                [TODO first line]
                 [second line]
                '''""");
  }

  // PY-6027
  @Test
  public void testNoContinuationWithoutProperIndent() {
    testTodos("class C: pass # [TODO todo]\n" +
              "#  unrelated comment line");
  }

  // PY-6027
  @Test
  public void testNewLineBetweenCommentLines() {
    testTodos("# [TODO first line]<caret>\n" +
              "#  [second line]");
    codeInsightFixture.get().type('\n');
    checkTodos("""
                 # [TODO first line]

                 #  second line""");
  }

  // TodoItemsTestCase skipped the tests below for this language. They keep its test names.
  @Test
  public void testNewTodoAfterUnsavedEditing() {
  }
}
