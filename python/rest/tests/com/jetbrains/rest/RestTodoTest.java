// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.rest;

import com.intellij.psi.search.PsiTodoSearchHelper;
import com.intellij.psi.search.TodoItem;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

public class RestTodoTest extends BasePlatformTestCase {
  public void testTodo() {
    myFixture.configureByText(RestFileType.INSTANCE, ".. TODO whatever \n");
    TodoItem[] items = PsiTodoSearchHelper.SERVICE.getInstance(getProject()).findTodoItems(myFixture.getFile());
    assertEquals(1, items.length);
  }
}
