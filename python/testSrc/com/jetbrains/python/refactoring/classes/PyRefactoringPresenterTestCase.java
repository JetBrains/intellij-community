package com.jetbrains.python.refactoring.classes;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.refactoring.classes.membersManager.PyMemberInfo;
import com.jetbrains.python.refactoring.classes.membersManager.vp.MembersBasedPresenter;
import com.jetbrains.python.refactoring.classes.membersManager.vp.MembersBasedView;
import com.jetbrains.python.refactoring.classes.membersManager.vp.MembersViewInitializationInfo;
import org.easymock.Capture;
import org.easymock.EasyMock;
import org.easymock.internal.MocksControl;
import org.hamcrest.Matcher;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

import java.util.Collection;

/**
 * Base class for {@link com.jetbrains.python.refactoring.classes.membersManager.vp.MembersBasedPresenter} tests.
 * It takes file <pre>file.py</pre> from folder provided as "refactoringName" constructor argument.
 * It configures {@link #myView}
 *
 * @param <C> configuration class for presenter view
 * @param <V> presenter's view class
 * @author Ilya.Kazakevich
 */
public abstract class PyRefactoringPresenterTestCase<C extends MembersViewInitializationInfo, V extends MembersBasedView<C>>
  extends PyTestCase {
  /**
   * Converts class collection to name collection
   */
  protected static final Function<PyClass, String> CLASS_TO_NAME = new ClassToName();
  /**
   * Easy mock control
   */
  protected MocksControl myMocksControl;
  /**
   * View mock
   */
  protected V myView;
  /**
   * Capture that stores config ({@link MembersViewInitializationInfo} or its children) after {@link com.jetbrains.python.vp.Presenter#launch()} is called
   */
  protected Capture<C> myViewConfigCapture;

  @NotNull
  private final Class<V> myViewClass;
  @NotNull
  private final String myRefactoringName;

  /**
   * @param viewClass view class
   * @param refactoringName name of the refactoring. Folder with its name would be used to take "file.py" from it.
   */
  protected PyRefactoringPresenterTestCase(@NotNull final Class<V> viewClass, @NotNull final String refactoringName) {
    myViewClass = viewClass;
    this.myRefactoringName = refactoringName;
  }


  /**
   * Compares member states (names and other fields) with provided matcher.
   * @see  PyPresenterTestMemberEntry
   * @param members members to compare
   * @param matcher to match them against
   */
  protected static void compareMembers(@NotNull final Collection<PyPresenterTestMemberEntry> members,
                                       @NotNull final Matcher<Iterable<? extends PyPresenterTestMemberEntry>> matcher) {
    Assert.assertThat("Wrong members or their states", members, matcher);
  }

  /**
   * Initializes test. Always run it <strong>first</strong> if overwrite.
   */
  @Override
  public void setUp() throws Exception {
    super.setUp();

    myFixture.copyDirectoryToProject("refactoring/" + myRefactoringName + "/presenter/", "");
    myFixture.configureFromTempProjectFile("file.py");
    myMocksControl = new MocksControl(MocksControl.MockType.NICE);
    myView = myMocksControl.createMock(myViewClass);

    configureMockCapture();
  }

  /**
   * Configures view to capture config info
   */
  private void configureMockCapture() {
    myViewConfigCapture = new Capture<>();

    myView.configure(EasyMock.capture(myViewConfigCapture));
    EasyMock.expectLastCall().once();
  }


  /**
   * @return collection of members displayed by presenter
   */
  @NotNull
  protected Collection<PyPresenterTestMemberEntry> getMembers() {
    Assert.assertTrue("No members captured", myViewConfigCapture.hasCaptured());
    final Collection<PyMemberInfo<PyElement>> members = myViewConfigCapture.getValue().getMemberInfos();
    Assert.assertFalse("No members selected", members.isEmpty());
    return Collections2.transform(members, new NameAndStatusTransformer(myViewConfigCapture.getValue().getMemberInfoModel()));
  }

  private static class ClassToName implements Function<PyClass, String> {

    @Override
    public String apply(@NotNull final PyClass input) {
      return input.getName();
    }
  }

  /**
   * Returns member infos presenter wants to display
   * @param presenter presenter to check
   * @return collection of member infos
   */
  @NotNull
  protected Collection<PyMemberInfo<PyElement>> getMemberInfos(@NotNull MembersBasedPresenter presenter) {
    myMocksControl.replay();
    presenter.launch();
    final Collection<PyMemberInfo<PyElement>> result = myViewConfigCapture.getValue().getMemberInfos();
    myMocksControl.reset();
    configureMockCapture();
    return result;
  }
}
