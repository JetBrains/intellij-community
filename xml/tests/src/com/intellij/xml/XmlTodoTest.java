// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xml;

import com.intellij.editor.TodoItemsTestCase;

public class XmlTodoTest extends TodoItemsTestCase {
  public void testSimple() {
    testTodos("<root>\n" +
              "<!-- [TODO to do ]-->\n" +
              "</root>\n");
  }

  public void testMultilineSingleComment() {
    testTodos("<root>\n" +
              "<!-- [TODO to do]\n" +
              "      [me ]-->\n" +
              "</root>\n");
  }

  public void testMultiline() {
    testTodos("<root>\n" +
              "<!-- [TODO to do ]-->\n" +
              "<!--  [me ]-->\n" +
              "</root>\n");
  }

  @Override
  protected String getFileExtension() {
    return "xml";
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
