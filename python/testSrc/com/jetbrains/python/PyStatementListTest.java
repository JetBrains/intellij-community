// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.intellij.openapi.command.WriteCommandAction;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyStatementList;


public class PyStatementListTest extends PyTestCase {
  public void testOneLineList() {
    PyElementGenerator generator = PyElementGenerator.getInstance(myFixture.getProject());
    PyFunction function = generator.createPhysicalFromText(LanguageLevel.PYTHON27, PyFunction.class, "def foo(): print 1");
    PyFunction function2 = generator.createPhysicalFromText(LanguageLevel.PYTHON27, PyFunction.class, "def foo(): print 2");
    final PyStatementList list1 = function.getStatementList();
    final PyStatementList list2 = function2.getStatementList();

    WriteCommandAction.writeCommandAction(myFixture.getProject()).run(() -> list1.add(list2.getStatements()[0]));

    assertEquals("def foo():\n    print 1\n    print 2", function.getText());
  }
}
