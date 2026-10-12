// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xml;

import com.intellij.editor.TodoItemsTestUtil;
import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.moduleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;

@TestApplication
public class XmlTodoTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<Path> pathFixture = tempPathFixture();
  @SuppressWarnings("unused")
  private final TestFixture<Module> moduleFixture = moduleFixture(projectFixture, pathFixture, true);
  private final TestFixture<CodeInsightTestFixture> codeInsightFixture = codeInsightFixture(projectFixture, pathFixture);

  private void testTodos(String text) {
    TodoItemsTestUtil.testTodos(codeInsightFixture.get(), "xml", text);
  }

  @Test
  public void testSimple() {
    testTodos("""
                <root>
                <!-- [TODO to do ]-->
                </root>
                """);
  }

  @Test
  public void testMultilineSingleComment() {
    testTodos("""
                <root>
                <!-- [TODO to do]
                      [me ]-->
                </root>
                """);
  }

  @Test
  public void testMultiline() {
    testTodos("""
                <root>
                <!-- [TODO to do ]-->
                <!--  [me ]-->
                </root>
                """);
  }

  // TodoItemsTestCase skipped the tests below for this language. They keep its test names.
  @Test
  public void testSuccessiveLineComments() {
  }

  @Test
  public void testSuccessiveLineCommentsAfterEditing() {
  }

  @Test
  public void testNewTodoAfterUnsavedEditing() {
  }

  @Test
  public void testAllLinesLoseHighlightingWithFirstLine() {
  }

  @Test
  public void testContinuationIsNotOverlappedWithFollowingTodo() {
  }

  @Test
  public void testContinuationInBlockCommentWithStars() {
  }
}
