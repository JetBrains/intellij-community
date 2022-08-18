// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.rest;

import com.intellij.psi.search.PsiTodoSearchHelper;
import com.intellij.psi.search.TodoItem;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

public class RestTodoTest extends BasePlatformTestCase {
  public void testTodo() {
    myFixture.configureByText(RestFileType.INSTANCE, ".. TODO whatever \n");
    TodoItem[] items = PsiTodoSearchHelper.getInstance(getProject()).findTodoItems(myFixture.getFile());
    assertEquals(1, items.length);
  }
}
