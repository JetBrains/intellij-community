// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.intellij.editor.TodoItemsTestCase;
import com.intellij.ide.todo.TodoConfiguration;
import com.intellij.psi.search.TodoAttributesUtil;
import com.intellij.psi.search.TodoPattern;
import com.intellij.testFramework.PlatformTestUtil;

public class PyTodoTest extends TodoItemsTestCase {

  public void testTodo() {
    testTodos("# [TODO: return dead parrot]\n");
  }

  public void testCustomTodo() {
    final TodoConfiguration todo = TodoConfiguration.getInstance();
    final TodoPattern[] oldPatterns = todo.getTodoPatterns();
    try {
      todo.setTodoPatterns(new TodoPattern[]{new TodoPattern("\\bDas\\b.*", TodoAttributesUtil.createDefault(), false)});
      PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
      testTodos("# [Das Rindfleischetikettierungsueberwachungsaufgabenuebertragungsgesetz]\n");
    }
    finally {
      todo.setTodoPatterns(oldPatterns);
    }
  }

  // PY-11040
  public void testTodoInDocstrings() {
    testTodos("''' [TODO: return dead parrot ]'''\n" +
              "def foo():\n''' [TODO: return dead parrot ]'''\n");
  }

  // PY-11040
  public void testTodoInTripleQuotedString() {
    testTodos("s = ''' TODO: return dead parrot '''");
  }

  // PY-6027
  @Override
  public void testSuccessiveLineComments() {
    testTodos("# [TODO first line]\n" +
              "#  [second line]");
  }

  // PY-6027
  @Override
  public void testSuccessiveLineCommentsAfterEditing() {
    testTodos("# [TODO first line]\n" +
              "# <caret>second line");
    type("     ");
    checkTodos("# [TODO first line]\n" +
               "#      [second line]");
  }

  // PY-6027
  @Override
  public void testAllLinesLoseHighlightingWithFirstLine() {
    testTodos("# [TO<caret>DO first line]\n" +
              "#  [second line]");
    delete();
    checkTodos("# TOO first line\n" +
               "#  second line");
  }

  // PY-6027
  @Override
  public void testContinuationIsNotOverlappedWithFollowingTodo() {
    testTodos("# [TODO first line]\n" +
              "#  [TODO second line]");
  }

  // PY-6027
  @Override
  public void testContinuationInBlockCommentWithStars() {
    testTodos("'''\n" +
              "[TODO first line]\n" +
              " [second line]\n" +
              "'''");
  }

  // PY-6027
  public void testNoContinuationWithoutProperIndent() {
    testTodos("class C: pass # [TODO todo]\n" +
              "#  unrelated comment line");
  }

  // PY-6027
  public void testNewLineBetweenCommentLines() {
    testTodos("# [TODO first line]<caret>\n" +
              "#  [second line]");
    type('\n');
    checkTodos("# [TODO first line]\n" +
               "\n" +
               "#  second line");
  }

  @Override
  protected String getFileExtension() {
    return PythonFileType.INSTANCE.getDefaultExtension();
  }

  @Override
  protected boolean supportsCStyleSingleLineComments() {
    return false;
  }

  @Override
  protected boolean supportsCStyleMultiLineComments() {
    return false;
  }
}
