package com.jetbrains.python.refactoring.classes.pullUp;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.intellij.refactoring.classMembers.MemberInfoModel;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.refactoring.classes.PyMemberInfoStorage;
import com.jetbrains.python.refactoring.classes.membersManager.PyMemberInfo;
import org.easymock.Capture;
import org.easymock.EasyMock;
import org.easymock.internal.MocksControl;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

import java.util.Collection;
import java.util.List;


/**
 * @author Ilya.Kazakevich
 */
public class PyPullUpPresenterTest extends PyTestCase {
  private static final ClassToName CLASS_TO_NAME = new ClassToName();
  private MocksControl myMocksControl;
  private PyPullUpView myView;
  private Capture<Collection<PyClass>> myParentsCapture;
  private Capture<List<PyMemberInfo>> myMemberInfos;

  @Override
  public void setUp() throws Exception {
    super.setUp();

    //TODO: Extract to some shared place?
    myFixture.copyDirectoryToProject("refactoring/pullup/presenter/", "");
    myFixture.configureFromTempProjectFile("file.py");
    myMocksControl = new MocksControl(MocksControl.MockType.NICE);
    myView = myMocksControl.createMock(PyPullUpView.class);

    myParentsCapture = new Capture<Collection<PyClass>>();
    Capture<MemberInfoModel<PyElement, PyMemberInfo>> memInfoModelCapture = new Capture<MemberInfoModel<PyElement, PyMemberInfo>>();
    myMemberInfos = new Capture<List<PyMemberInfo>>();

    myView.init(EasyMock.capture(myParentsCapture), EasyMock.capture(memInfoModelCapture), EasyMock.capture(myMemberInfos));
    EasyMock.expectLastCall().once();
  }

  /**
   * Checks that parents are returned in MRO order and no parents outside of source root are included
   */
  public void testParentsOrder() throws Exception {
    PyPullUpPresenter sut = configureByClass("Child");

    myMocksControl.replay();

    sut.launch();
    Assert.assertTrue("Presenter did not show parents", myParentsCapture.hasCaptured());
    Collection<PyClass> parents = myParentsCapture.getValue();
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
   * Checks that some members are not allowed, while others are
   */
  public void testMembers() throws Exception {
    PyPullUpPresenterImpl sut = configureByClass("HugeChild");
    EasyMock.expect(myView.getSelectedParent()).andReturn(getClassByName("SubParent1")).anyTimes();

    myMocksControl.replay();
    sut.launch();

    Assert.assertTrue("No members selected", myMemberInfos.hasCaptured());
    List<PyMemberInfo> members = myMemberInfos.getValue();
    Assert.assertFalse("No members selected", members.isEmpty());
    final Collection<Entry> memberNamesAndStatus = Collections2.transform(members, new NameAndStatusTransformer(sut));

    //Pair will return correct type
    final Matcher<Iterable<? extends Entry>> matcher = Matchers
      .containsInAnyOrder(new Entry("extends date", true, false),
                          new Entry("CLASS_FIELD", true, true),
                          new Entry("__init__(self)", true, false),
                          new Entry("extends SubParent1", false, false),
                          new Entry("foo(self)", false, false),
                          new Entry("bar(self)", true, false),
                          new Entry("static_1(cls)", true, true),
                          new Entry("static_2()", true, true),
                          new Entry("self.instance_field_1", true, false),
                          new Entry("self.instance_field_2", true, false),
                          new Entry("bad_method()", true, false)
      );
    Assert.assertThat("Wrong members or their states", memberNamesAndStatus, matcher);


  }

  /**
   * Checks that refactoring does not work for classes with out of members
   */
  private void ensureNoMembers(String className) throws Exception {
    try {
      PyPullUpPresenter sut = configureByClass(className);

      myMocksControl.replay();
      sut.launch();
    }
    catch (IllegalArgumentException ignored) {
      return;
    }
    Assert.fail("Presenter should throw exception, but it returned list of parents instead: " + myParentsCapture.getValue());
  }


  private PyPullUpPresenterImpl configureByClass(String name) {
    PyClass childClass = getClassByName(name);
    PyMemberInfoStorage storage = new PyMemberInfoStorage(childClass);
    return new PyPullUpPresenterImpl(myView, storage, childClass);
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

  private static class NameAndStatusTransformer implements Function<PyMemberInfo, Entry> {
    private final PyPullUpPresenterImpl presenter;

    private NameAndStatusTransformer(PyPullUpPresenterImpl presenter) {
      this.presenter = presenter;
    }

    @Override
    public Entry apply(final PyMemberInfo input) {
      return new Entry(input.getDisplayName(), presenter.isMemberEnabled(input), input.isStatic());
    }
  }

  private static class Entry {
    @NonNls @NotNull
    private final String myName;
    private final boolean myEnabled;
    private final boolean myStaticEntry;

    private Entry(@NotNull final String name, final boolean enabled, final boolean staticEntry) {
      myName = name;
      myEnabled = enabled;
      myStaticEntry = staticEntry;
    }

    @Override
    public String toString() {
      return "Entry{" +
             "myName='" + myName + '\'' +
             ", myEnabled=" + myEnabled +
             ", myStaticEntry=" + myStaticEntry +
             '}';
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) return true;
      if (!(o instanceof Entry)) return false;

      final Entry entry = (Entry)o;

      if (myEnabled != entry.myEnabled) return false;
      if (myStaticEntry != entry.myStaticEntry) return false;
      if (!myName.equals(entry.myName)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = myName.hashCode();
      result = 31 * result + (myEnabled ? 1 : 0);
      result = 31 * result + (myStaticEntry ? 1 : 0);
      return result;
    }
  }
}
