package com.jetbrains.python.refactoring.classes.pullUp;

import com.google.common.collect.Iterables;
import com.intellij.refactoring.classMembers.MemberInfoModel;
import com.jetbrains.NotNullPredicate;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.refactoring.classes.PyMemberInfoStorage;
import com.jetbrains.python.refactoring.classes.membersManager.PyMemberInfo;
import org.easymock.internal.MocksControl;
import org.hamcrest.Matchers;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests dependencies management for PyPullUp refactoring
 *
 * @author Ilya.Kazakevich
 */
public class PyPullUpInfoModelTest extends PyTestCase {
  private PyPullUpInfoModel mySut;
  private List<PyMemberInfo<PyElement>> myMemberInfos;


  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFixture.configureByFile("/refactoring/pullup/pyPullUpInfoModel.py");
    final PyClass childClass = getClassByName("ChildWithDependencies");
    final PyClass parentClass = getClassByName("SomeParent");
    mySut = new PyPullUpInfoModel(childClass, new MocksControl(MocksControl.MockType.NICE).createMock(PyPullUpView.class));
    mySut.setSuperClass(parentClass);
    myMemberInfos = new PyMemberInfoStorage(childClass).getClassMemberInfos(childClass);
  }

  /**
   * Checks class field depends on class field
   */
  public void testClassMemberDependencies() {
    checkMembers("CLASS_FIELD_DEPENDS_ON_CLASS_FIELD_FOO");
    Assert.assertThat("Class member dependencies failed", getErrorMemberNames(), Matchers.containsInAnyOrder("CLASS_FIELD_FOO"));
  }

  /**
   * Checks instance field depends on class field
   */
  public void testInstanceMemberDependencies() {
    checkMembers("self.depends_on_class_field_foo");
    Assert.assertThat("Instance member dependencies failed", getErrorMemberNames(), Matchers.containsInAnyOrder("CLASS_FIELD_FOO"));
  }

  /**
   * Checks method depends on another class field
   */
  public void testMethodMemberDependencies() {
    checkMembers("method_depends_on_normal_method(self)");
    Assert.assertThat("Method dependencies failed", getErrorMemberNames(), Matchers.containsInAnyOrder("normal_method(self)"));
  }

  /**
   * Checks method depends on method
   */
  public void testMethodOnInstanceMemberDependencies() {
    checkMembers("method_depends_on_instance_field_bar(self)");
    Assert.assertThat("Instance on member dependencies failed", getErrorMemberNames(), Matchers.containsInAnyOrder("self.instance_field_bar"));
  }

  /**
   * Check dependnecies for properties, declared in old-style
   *
   */
  public void testOldProperty() {
    checkMembers("method_depends_on_old_property(self)");
    Assert.assertThat("Method on old property dependency failed", getErrorMemberNames(), Matchers.containsInAnyOrder(
      "old_property",
      "old_property_2",
      "old_property_3"));
  }

  /**
   *
   * Check dependnecies for properties, declared in new-style
   */
  public void testNewProperty() {
    checkMembers("method_depends_on_new_property(self)");
    Assert.assertThat("Method on new property dependency failed", getErrorMemberNames(), Matchers.containsInAnyOrder("new_property", "new_property_2"));
  }


  /**
   * All dependencies are met: new (destination) class has all of them
   */
  public void testParentDependenciesOk() {
    checkMembers("CLASS_FIELD_DEPENDS_ON_PARENT_FIELD",
                 "method_depends_on_parent_method(self)",
                 "method_depends_on_parent_field(self)");
    Assert.assertThat("Dependence check false positive: parent has all required members", getErrorMemberNames(), Matchers.empty());
  }


  /**
   * New (destination) class has no members, required by member under refactoring.
   * Error should be displayed.
   */
  public void testNoParentDependenciesOk() {
    mySut.setSuperClass(getClassByName("EmptyParent"));
    checkMembers("CLASS_FIELD_DEPENDS_ON_PARENT_FIELD",
                 "method_depends_on_parent_method(self)",
                 "method_depends_on_parent_field(self)");
    Assert.assertThat("Dependence check false positive: parent has all required members", getErrorMemberNames(), Matchers.contains("extends SomeParent"));
  }


  /**
   * @return names of error members (displayed with red)
   */
  @NotNull
  private List<String> getErrorMemberNames() {
    final List<String> result = new ArrayList<>();
    for (final PyMemberInfo<PyElement> info : myMemberInfos) {
      if (mySut.checkForProblems(info) != MemberInfoModel.OK) {
        result.add(info.getDisplayName());
      }
    }
    return result;
  }

  /**
   * Marks members to be moved (sets checkbox on them)
   * @param memberNames names of members to check
   */
  private void checkMembers(@NotNull final String... memberNames) {
    for (final String memberName : memberNames) {
      Iterables.find(myMemberInfos, new NamePredicate(memberName)).setChecked(true);
    }
    mySut.memberInfoChanged(myMemberInfos);
  }

  private static class NamePredicate extends NotNullPredicate<PyMemberInfo<?>> {
    @NotNull
    private final String myNameToSearch;

    private NamePredicate(@NotNull final String nameToSearch) {
      myNameToSearch = nameToSearch;
    }

    @Override
    protected boolean applyNotNull(@NotNull final PyMemberInfo<?> input) {
      return input.getDisplayName().equals(myNameToSearch);
    }
  }
}
