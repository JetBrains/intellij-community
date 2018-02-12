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

import com.intellij.ide.todo.TodoConfiguration;
import com.intellij.psi.search.PsiTodoSearchHelper;
import com.intellij.psi.search.TodoAttributesUtil;
import com.intellij.psi.search.TodoItem;
import com.intellij.psi.search.TodoPattern;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;

/**
 * @author traff
 */
public class PyTodoTest extends LightPlatformCodeInsightFixtureTestCase {
  public void testTodo() {
    myFixture.configureByText(PythonFileType.INSTANCE, "# TODO: return dead parrot\n");
    TodoItem[] items = PsiTodoSearchHelper.SERVICE.getInstance(getProject()).findTodoItems(myFixture.getFile());
    assertEquals(1, items.length);
  }

  public void testCustomTodo() {
    TodoConfiguration todo = TodoConfiguration.getInstance();
    TodoPattern[] oldPatterns = todo.getTodoPatterns();
    try {
      todo.setTodoPatterns(new TodoPattern[] {new TodoPattern("\\bDas\\b.*", TodoAttributesUtil.createDefault(), false)});
      myFixture.configureByText(PythonFileType.INSTANCE, "# Das Rindfleischetikettierungsueberwachungsaufgabenuebertragungsgesetz \n");
      TodoItem[] items = PsiTodoSearchHelper.SERVICE.getInstance(getProject()).findTodoItems(myFixture.getFile());
      assertEquals(1, items.length);
    } finally {
      todo.setTodoPatterns(oldPatterns);
    }
  }

  public void testTodoInDocstrings() {
    myFixture.configureByText(PythonFileType.INSTANCE, "''' TODO: return dead parrot '''");
    TodoItem[] items = PsiTodoSearchHelper.SERVICE.getInstance(getProject()).findTodoItems(myFixture.getFile());
    assertEquals(1, items.length);
  }

  public void testTodoInDocstrings2() {
    myFixture.configureByText(PythonFileType.INSTANCE, "def foo():\n''' TODO: return dead parrot '''");
    TodoItem[] items = PsiTodoSearchHelper.SERVICE.getInstance(getProject()).findTodoItems(myFixture.getFile());
    assertEquals(1, items.length);
  }

  public void testTodoInTripleQuotedString() {
    myFixture.configureByText(PythonFileType.INSTANCE, "s = ''' TODO: return dead parrot '''");
    TodoItem[] items = PsiTodoSearchHelper.SERVICE.getInstance(getProject()).findTodoItems(myFixture.getFile());
    assertEquals(0, items.length); // no todo in normal triple quoted strings
  }

}
