/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python;

import com.intellij.editor.TodoItemsTestCase;
import com.intellij.ide.todo.TodoConfiguration;
import com.intellij.psi.search.TodoAttributesUtil;
import com.intellij.psi.search.TodoPattern;

/**
 * @author traff
 */
public class PyTodoTest extends TodoItemsTestCase {

  public void testTodo() {
    testTodos("# [TODO: return dead parrot]\n");
  }

  public void testCustomTodo() {
    final TodoConfiguration todo = TodoConfiguration.getInstance();
    final TodoPattern[] oldPatterns = todo.getTodoPatterns();
    try {
      todo.setTodoPatterns(new TodoPattern[]{new TodoPattern("\\bDas\\b.*", TodoAttributesUtil.createDefault(), false)});
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
