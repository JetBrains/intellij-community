package com.jetbrains.python.refactoring.classes.pullUp;

import com.google.common.collect.Collections2;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.refactoring.classes.PyMemberInfoStorage;
import com.jetbrains.python.refactoring.classes.PyPresenterTestMemberEntry;
import com.jetbrains.python.refactoring.classes.PyRefactoringPresenterTestCase;
import org.easymock.EasyMock;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

import java.util.Collection;


/**
 * Test presenter for pull-up refactoring
 *
 * @author Ilya.Kazakevich
 */
public class PyPullUpPresenterTest extends PyRefactoringPresenterTestCase<PyPullUpViewInitializationInfo, PyPullUpView> {


  public PyPullUpPresenterTest() {
    super(PyPullUpView.class, "pullup");
  }

  /**
   * Checks that parents are returned in MRO order and no parents outside of source root are included
   */
  public void testParentsOrder() throws Exception {
    final PyPullUpPresenter sut = configureByClass("Child");

    myMocksControl.replay();

    sut.launch();
    Assert.assertTrue("Presenter did not show parents", myViewConfigCapture.hasCaptured());
    final Collection<PyClass> parents = myViewConfigCapture.getValue().getParents();
    Assert.assertThat("Wrong list of parents or parents are listed in wrong order", Collections2.transform(parents, CLASS_TO_NAME),
                      Matchers.contains("SubParent1", "SubParent2", "MainParent"));
  }

  /**
   * Checks that refactoring does not work for classes with out of allowed parents
   */
  public void testNoParents() throws Exception {
    ensureNoMembers("NoParentsAllowed");
  }

  /**
   * Checks that refactoring does not work for classes with out of members
   */
  public void testNoMembers() throws Exception {
    ensureNoMembers("NoMembers");
  }

  /**
   * Checks that refactoring does not work when C3 MRO can't be calculated
   */
  public void testBadMro() throws Exception {
    ensureNoMembers("BadMro");
  }

  /**
   * Checks that parent can't be moved to itself
   */
  public void testNoMoveParentToItSelf() throws Exception {
    final Collection<PyPresenterTestMemberEntry> memberNamesAndStatus = launchAndGetMembers("Foo", "Bar");

    compareMembers(memberNamesAndStatus, Matchers.containsInAnyOrder(new PyPresenterTestMemberEntry("__init__(self)", true, false),
                                                                     new PyPresenterTestMemberEntry("self.foo", true, false),
                                                                     new PyPresenterTestMemberEntry("extends Bar", false, false)));

  }

  /**
   * Checks that some members are not allowed, while others are
   */
  public void testMembers() throws Exception {
    final Collection<PyPresenterTestMemberEntry> memberNamesAndStatus = launchAndGetMembers("HugeChild", "SubParent1");

    //Pair will return correct type
    final Matcher<Iterable<? extends PyPresenterTestMemberEntry>> matcher = Matchers
      .containsInAnyOrder(new PyPresenterTestMemberEntry("extends date", true, false),
                          new PyPresenterTestMemberEntry("CLASS_FIELD", true, true),
                          new PyPresenterTestMemberEntry("__init__(self)", true, false),
                          new PyPresenterTestMemberEntry("extends SubParent1", false, false),
                          new PyPresenterTestMemberEntry("foo(self)", false, false),
                          new PyPresenterTestMemberEntry("bar(self)", true, false),
                          new PyPresenterTestMemberEntry("static_1(cls)", true, true),
                          new PyPresenterTestMemberEntry("static_2()", true, true),
                          new PyPresenterTestMemberEntry("self.instance_field_1", true, false),
                          new PyPresenterTestMemberEntry("self.instance_field_2", true, false),
                          new PyPresenterTestMemberEntry("bad_method()", true, false));
    compareMembers(memberNamesAndStatus, matcher);


  }

  /**
   * Launches presenter and returns members it displayed to user
   *
   * @param classUnderRefactoring class to refactor
   * @param destinationClass      where to move it
   * @return members displayed to user
   */
  @NotNull
  private Collection<PyPresenterTestMemberEntry> launchAndGetMembers(@NotNull final String classUnderRefactoring,
                                                                     @NotNull final String destinationClass) {
    final PyPullUpPresenterImpl sut = configureByClass(classUnderRefactoring);

    EasyMock.expect(myView.getSelectedParent()).andReturn(getClassByName(destinationClass)).anyTimes();

    myMocksControl.replay();
    sut.launch();

    return getMembers();
  }

  /**
   * Checks that refactoring does not work for classes with out of members
   */
  private void ensureNoMembers(@NotNull final String className) throws Exception {
    try {
      final PyPullUpPresenter sut = configureByClass(className);

      myMocksControl.replay();
      sut.launch();
    }
    catch (final IllegalArgumentException ignored) {
      return;
    }
    Assert
      .fail("Presenter should throw exception, but it returned list of parents instead: " + myViewConfigCapture.getValue().getParents());
  }

  /**
   * Creates presenter (sut) by class
   *
   * @param name name of class
   * @return presenter
   */
  private PyPullUpPresenterImpl configureByClass(@NotNull final String name) {
    final PyClass childClass = getClassByName(name);
    final PyMemberInfoStorage storage = new PyMemberInfoStorage(childClass);
    return new PyPullUpPresenterImpl(myView, storage, childClass);
  }


}
