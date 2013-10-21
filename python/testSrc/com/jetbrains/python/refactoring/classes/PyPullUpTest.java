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
package com.jetbrains.python.refactoring.classes;

import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.refactoring.classes.pullUp.PyPullUpHelper;

import java.util.Collections;

/**
 * @author Dennis.Ushakov
 */
public class PyPullUpTest extends PyClassRefactoringTest {
  public void testSimple() {
    doHelperTest("Boo", ".boo", "Foo");
  }

  public void testSuperclass() {
    doHelperTest("Boo", "Foo", "Zope");
  }

  public void testExistingsuperclass() {
    doHelperTest("Boo", "Foo", "Zope");
  }

  public void testWithComments() {
    doHelperTest("Boo", ".boo", "Foo");
  }

  public void testWithMultilineComments() {
    doHelperTest("Boo", ".boo", "Foo");
  }

  public void testMultiFile() {   // PY-2810
    doMultiFileTest();
  }

  public void testDuplicateImport() {  // PY-2810
    doMultiFileTest();
  }

  private void doMultiFileTest() {
    String baseName = "refactoring/pullup/" + getTestName(true) + "/";
    myFixture.copyFileToProject(baseName + "Class.py", "Class.py");
    myFixture.copyFileToProject(baseName + "SuperClass.py", "SuperClass.py");
    doPullUp("AnyClass", ".this_should_be_in_super", "SuperClass");
    myFixture.checkResultByFile("SuperClass.py", "/" + baseName + "/SuperClass.after.py", true);
  }

  private void doHelperTest(final String className, final String memberName, final String superClassName) {
    String baseName = "/refactoring/pullup/" + getTestName(true);
    myFixture.configureByFile(baseName + ".py");
    doPullUp(className, memberName, superClassName);
    myFixture.checkResultByFile(baseName + ".after.py");
  }

  private void doPullUp(String className, String memberName, String superClassName) {
    final PyClass clazz = findClass(className);
    final PyElement member = findMember(className, memberName);
    final PyClass superClass = findClass(superClassName);
    PyPullUpHelper.pullUp(clazz, Collections.singleton(new PyMemberInfo(member)), superClass);
  }
}
