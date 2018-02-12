/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.openapi.command.WriteCommandAction;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyStatementList;

/**
 * @author yole
 */
public class PyStatementListTest extends PyTestCase {
  public void testOneLineList() {
    PyElementGenerator generator = PyElementGenerator.getInstance(myFixture.getProject());
    PyFunction function = generator.createPhysicalFromText(LanguageLevel.PYTHON27, PyFunction.class, "def foo(): print 1");
    PyFunction function2 = generator.createPhysicalFromText(LanguageLevel.PYTHON27, PyFunction.class, "def foo(): print 2");
    final PyStatementList list1 = function.getStatementList();
    final PyStatementList list2 = function2.getStatementList();

    new WriteCommandAction.Simple(myFixture.getProject()) {
      @Override
      protected void run() {
        list1.add(list2.getStatements()[0]);
      }
    }.execute();

    assertEquals("def foo():\n    print 1\n    print 2", function.getText());
  }
}
