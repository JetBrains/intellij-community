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
package org.intellij.plugins.markdown.todo;

import com.intellij.ide.todo.TodoConfiguration;
import com.intellij.psi.search.PsiTodoSearchHelper;
import com.intellij.psi.search.TodoAttributesUtil;
import com.intellij.psi.search.TodoPattern;
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

  private void doTest(@NotNull String text, int expectedTodos) {
    myFixture.configureByText(MarkdownFileType.INSTANCE, text);
    assertEquals(expectedTodos, PsiTodoSearchHelper.SERVICE.getInstance(getProject()).findTodoItems(myFixture.getFile()).length);
  }

  public void testCustomTodo() {
    TodoConfiguration todo = TodoConfiguration.getInstance();
    TodoPattern[] oldPatterns = todo.getTodoPatterns();
    try {
      todo.setTodoPatterns(new TodoPattern[]{new TodoPattern("\\btodo comment\\b.*", TodoAttributesUtil.createDefault(), false)});
      doTest("[//]: #s (todo comment)", 1);
    }
    finally {
      todo.setTodoPatterns(oldPatterns);
    }
  }
}
