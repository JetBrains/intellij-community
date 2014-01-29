package com.jetbrains.python.refactoring.classes.pullUp;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.intellij.openapi.util.Pair;
import com.intellij.refactoring.classMembers.MemberInfoModel;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.refactoring.classes.PyMemberInfo;
import com.jetbrains.python.refactoring.classes.PyMemberInfoStorage;
import org.easymock.Capture;
import org.easymock.EasyMock;
import org.easymock.internal.MocksControl;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

import java.util.Collection;
import java.util.List;


/**
 * @author Ilya.Kazakevich
 */
public class PullUpPresenterTest extends PyTestCase {
  private static final ClassToName CLASS_TO_NAME = new ClassToName();
  private MocksControl mocksControl;
  private PullUpView view;
  private Capture<Collection<PyClass>> parentsCapture;
  private Capture<List<PyMemberInfo>> memberInfos;

  public void setUp() throws Exception {
    super.setUp();

    //TODO: Extract to some shared place?
    myFixture.copyDirectoryToProject("refactoring/pullup/presenter/", "");
    myFixture.configureFromTempProjectFile("file.py");
    mocksControl = new MocksControl(MocksControl.MockType.NICE);
    view = mocksControl.createMock(PullUpView.class);

    parentsCapture = new Capture<Collection<PyClass>>();
    Capture<MemberInfoModel<PyElement, PyMemberInfo>> memInfoModelCapture = new Capture<MemberInfoModel<PyElement, PyMemberInfo>>();
    memberInfos = new Capture<List<PyMemberInfo>>();

    view.init(EasyMock.capture(parentsCapture), EasyMock.capture(memInfoModelCapture), EasyMock.capture(memberInfos));
    EasyMock.expectLastCall().once();
  }

  /**
   * Checks that parents are returned in MRO order and no parents outside of source root are included
   */
  public void testParentsOrder() throws Exception {
    PullUpPresenter sut = configureByClass("Child");

    mocksControl.replay();

    sut.launch();
    Assert.assertTrue("Presenter did not show parents", parentsCapture.hasCaptured());
    Collection<PyClass> parents = parentsCapture.getValue();
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
   * Checks that some members are not allowed
   */
  public void testDisabledMembers() throws Exception {
    PullUpPresenterImpl sut = configureByClass("SomeMembersDisabled");
    EasyMock.expect(view.getSelectedParent()).andReturn(getClassByName("SubParent1")).anyTimes();

    mocksControl.replay();
    sut.launch();

    Assert.assertTrue("No members selected", memberInfos.hasCaptured());
    List<PyMemberInfo> members = memberInfos.getValue();
    Assert.assertFalse("No members selected", members.isEmpty());
    Collection<Pair<String, Boolean>> memberNamesAndStatus = Collections2.transform(members, new NameAndStatusTransformer(sut));

    //Pair will return correct type
    @SuppressWarnings("unchecked") Matcher<Iterable<? extends Pair<String, Boolean>>> matcher = Matchers
      .containsInAnyOrder(
        Pair.create("date", true),
        Pair.create("SubParent1", false),
        Pair.create("foo", false),
        Pair.create("bar", true));
    Assert.assertThat("Wrong members or their states", memberNamesAndStatus, matcher);


  }

  /**
   * Checks that refactoring does not work for classes with out of members
   */
  private void ensureNoMembers(String className) throws Exception {
    try {
      PullUpPresenter sut = configureByClass(className);

      mocksControl.replay();
      sut.launch();
    }
    catch (IllegalArgumentException ignored) {
      return;
    }
    Assert.fail("Presenter should throw exception, but it returned list of parents instead: " + parentsCapture.getValue());
  }



  private PullUpPresenterImpl configureByClass(String name) {
    PyClass childClass = getClassByName(name);
    PyMemberInfoStorage storage = new PyMemberInfoStorage(childClass);
    return new PullUpPresenterImpl(view, storage, childClass);
  }

  @NotNull
  private PyClass getClassByName(String name) {
    return myFixture.findElementByText("class " + name, PyClass.class);
  }

  private static class ClassToName implements Function<PyClass, String> {

    @Override
    public String apply(PyClass input) {
      return input.getName();
    }
  }

  private static class NameAndStatusTransformer implements Function<PyMemberInfo, Pair<String, Boolean>> {
    private final PullUpPresenterImpl presenter;

    private NameAndStatusTransformer(PullUpPresenterImpl presenter) {
      this.presenter = presenter;
    }

    @Override
    public Pair<String, Boolean> apply(PyMemberInfo input) {
      PyElement member = input.getMember();
      return Pair.create(member.getName(), presenter.isMemberEnabled(input));
    }
  }
}
