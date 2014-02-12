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
package com.jetbrains.python.refactoring.classes.pullUp;

import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.refactoring.classes.PyClassRefactoringTest;
import com.jetbrains.python.refactoring.classes.membersManager.MembersManager;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

/**
 * @author Dennis.Ushakov
 */
public class PyPullUpTest extends PyClassRefactoringTest {

  public PyPullUpTest() {
    super("pullup");
  }

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

  public void testSeveralParents() {
    doHelperTest("Child", "Spam", "Parent_1");
  }

  public void testMoveClassAttributesSimple() {
    doHelperTest("Child", "#CLASS_VAR", "Parent");
  }

  public void testMoveClassAttributesNoPass() {
    doHelperTest("Child2", "#CLASS_VAR", "Parent2");
  }

  public void testMoveInstanceAttributesSimple() {
    doHelperTest("Child", "#instance_field", "Parent");
  }

  public void testMoveInstanceAttributesNoInit() {
    doHelperTest("Child", "#instance_field", "Parent");
  }

  public void testMoveInstanceAttributesLeaveEmptyInit() {
    doHelperTest("Child", "#foo", "Parent");
  }

  public void testMultiFile() {   // PY-2810
    doMultiFileTest();
  }

  public void testDuplicateImport() {  // PY-2810
    doMultiFileTest();
  }

  public void testFieldMove() {
    final String[] modules = {"Class", "SuperClass"};
    configureMultiFile(modules);
    doPullUp("AnyClass", "#COPYRIGHT", "SuperClass");
    doPullUp("AnyClass", "#version", "SuperClass");
    checkMultiFile(modules);
  }

  private void doMultiFileTest() {
    final String[] modules = {"Class", "SuperClass"};
    configureMultiFile(modules);
    doPullUp("AnyClass", ".this_should_be_in_super", "SuperClass");
    checkMultiFile(modules);
  }

  private void doHelperTest(final String className, final String memberName, final String superClassName) {
    myFixture.configureByFile(getMultiFileBaseName() + ".py");
    doPullUp(className, memberName, superClassName);
    myFixture.checkResultByFile(getMultiFileBaseName() + ".after.py");
  }

  private void doPullUp(String className, String memberName, String superClassName) {
    final PyClass clazz = findClass(className);
    final PyElement member = findMember(className, memberName);
    final PyClass superClass = findClass(superClassName);
    moveViaProcessor(clazz.getProject(),
                     new PyPullUpProcessor(clazz, superClass, Collections.singleton(MembersManager.findMember(clazz, member))));
  }
}
