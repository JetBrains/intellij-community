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
package com.jetbrains.python.refactoring.classes.pushDown;

import com.intellij.util.ArrayUtil;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.refactoring.classes.PyClassRefactoringTest;
import com.jetbrains.python.refactoring.classes.membersManager.MembersManager;
import com.jetbrains.python.refactoring.classes.membersManager.PyMemberInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Dennis.Ushakov
 */
public class PyPushDownTest extends PyClassRefactoringTest {

  public PyPushDownTest() {
    super("pushdown");
  }

  // Tests that pushing down methods moves imports as well (PY-10963)
  public void testMultiFileImports() {
    final String[] modules = {"child_module", "parent_module"};
    configureMultiFile(ArrayUtil.mergeArrays(modules, "shared_module"));
    final PyClass parentClass = findClass("Parent");
    final PyMemberInfo<PyElement> methodToMove = MembersManager.findMember(parentClass, findMember("Parent", ".should_be_pushed"));

    moveViaProcessor(myFixture.getProject(), new PyPushDownProcessor(myFixture.getProject(), Collections.singletonList(methodToMove), parentClass));
    checkMultiFile(modules);
  }

  public void testSimple() {
    doProcessorTest("Foo", null, ".foo");
  }

  public void testSuperclass() {
    doProcessorTest("Zope", null, "Foo");
  }

  public void testFull() {
    doProcessorTest("Parent", null, "#CLASS_VAR_1", "#inst_var", ".method_1", "Dummny");
  }

  public void testMultiple() {
    doProcessorTest("Foo", null, ".foo");
  }

  public void testPy346() {
    doProcessorTest("A", null, ".meth_a1", ".meth_a2");
  }

  public void testExistingmethod() {
    doProcessorTest("Foo",
                    "method <b><code>foo</code></b> is already overridden in class <b><code>Boo</code></b>. Method will not be pushed down to that class.",
                    ".foo");
  }

  private void doProcessorTest(final String className, final String expectedError, final String... memberNames) {
    try {
      String baseName = "/refactoring/pushdown/" + getTestName(true);
      myFixture.configureByFile(baseName + ".before.py");
      final PyClass clazz = findClass(className);
      final List<PyMemberInfo<PyElement>> members = new ArrayList<>();
      for (String memberName : memberNames) {
        final PyElement member = findMember(className, memberName);
        members.add(MembersManager.findMember(clazz, member));
      }

      final PyPushDownProcessor processor = new PyPushDownProcessor(myFixture.getProject(), members, clazz);
      moveViaProcessor(myFixture.getProject(), processor);
      myFixture.checkResultByFile(baseName + ".after.py");
    }
    catch (Exception e) {
      if (expectedError == null) throw e;
      assertTrue(e.getMessage(), e.getMessage().contains(expectedError));
    }
  }
}
