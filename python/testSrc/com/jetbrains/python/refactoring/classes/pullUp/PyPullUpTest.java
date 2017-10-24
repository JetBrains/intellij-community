// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.refactoring.classes.pullUp;

import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.refactoring.classes.PyClassRefactoringTest;
import com.jetbrains.python.refactoring.classes.membersManager.MembersManager;
import com.jetbrains.python.refactoring.classes.membersManager.PyMemberInfo;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;

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

  public void testInstanceNotDeclaredInInit() {
    doHelperTest("Child", "#foo", "Parent");
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

  // Ensures than all fields are moved in correct order
  public void testDependenciesOrder() {
    doHelperTestSeveralMembers("Child", "Parent", "#CLASS_FIELD", "#ANOTHER_CLASS_FIELD", "#FIELD", ".foo", "#SOME_VAR", "#b", "#d", "#A_FIELD");
  }

  public void testMultiFile() {   // PY-2810
    doMultiFileTest();
  }

  public void testDuplicateImport() {  // PY-2810
    doMultiFileTest();
  }

  public void testProperties() {
    final String[] modules = {"Class", "SuperClass"};
    configureMultiFile(modules);
    doPullUp("AnyClass", "SuperClass", ".new_property");
    checkMultiFile(modules);
  }

  public void testFieldMove() {
    final String[] modules = {"Class", "SuperClass"};
    configureMultiFile(modules);
    doPullUp("AnyClass", "SuperClass", "#COPYRIGHT");
    doPullUp("AnyClass", "SuperClass", "#version");
    checkMultiFile(modules);
  }

  // PY-16747
  public void testAbstractMethodDocStringIndentationPreserved() {
    myFixture.configureByFile(getMultiFileBaseName() + ".py");
    doPullUp("B", "A", true, ".m");
    myFixture.checkResultByFile(getMultiFileBaseName() + ".after.py");
  }

  // PY-16770
  public void testAbstractMethodDocStringPrefixPreserved() {
    myFixture.configureByFile(getMultiFileBaseName() + ".py");
    doPullUp("B", "A", true, ".m");
    myFixture.checkResultByFile(getMultiFileBaseName() + ".after.py");
  }

  private void doMultiFileTest() {
    final String[] modules = {"Class", "SuperClass"};
    configureMultiFile(modules);
    doPullUp("AnyClass", "SuperClass", ".this_should_be_in_super");
    checkMultiFile(modules);
  }

  /**
   * Ensures that pulling abstract method up to class that already uses ABCMeta works correctly
   */
  public void testAbstractMethodHasMeta() {
    checkAbstract(".my_method");
  }

  /**
   * Ensures that pulling abstract method up to class that has NO ABCMeta works correctly for py2 (__metaclass__ is added)
   */
  public void testAbstractMethodPy2AddMeta() {
    checkAbstract(".my_method", ".my_method_2");
  }

  /**
   * Ensures that pulling abstract method up to class that has NO ABCMeta works correctly for py3k (metaclass is added)
   */
  public void testAbstractMethodPy3AddMeta() {
    runWithLanguageLevel(LanguageLevel.PYTHON34, () -> checkAbstract(".my_method", ".my_class_method"));
  }

  /**
   * Moves methods fromn Child to Parent and make them abstract
   * @param methodNames methods to check
   */
  private void checkAbstract(@NotNull final String... methodNames) {
    final String[] modules = {"Class", "SuperClass"};
    configureMultiFile(modules);
    doPullUp("Child", "Parent", true, methodNames);
    checkMultiFile(modules);
  }


  private void doHelperTest(final String className, final String memberName, final String superClassName) {
    doHelperTestSeveralMembers(className, superClassName, memberName);
  }

  private void doHelperTestSeveralMembers(@NotNull final String className, @NotNull final String superClassName, @NotNull final String... memberNames) {
    myFixture.configureByFile(getMultiFileBaseName() + ".py");
    doPullUp(className, superClassName, false, memberNames);
    myFixture.checkResultByFile(getMultiFileBaseName() + ".after.py");
  }

  private void doPullUp(final String className, final String superClassName, final String memberName) {
    doPullUp(className, superClassName, false, memberName);
  }
  private void doPullUp(final String className, final String superClassName, final boolean toAbstract, final String... memberNames ) {
    final PyClass clazz = findClass(className);
    final PyClass superClass = findClass(superClassName);
    final Collection<PyMemberInfo<PyElement>> membersToMove = new ArrayList<>(memberNames.length);
    for (final String memberName : memberNames) {
      final PyElement member = findMember(className, memberName);
      final PyMemberInfo<PyElement> memberInfo = MembersManager.findMember(clazz, member);
      memberInfo.setToAbstract(toAbstract);
      membersToMove.add(memberInfo);
    }
    moveViaProcessor(clazz.getProject(),
                     new PyPullUpProcessor(clazz, superClass, membersToMove));
  }
}
