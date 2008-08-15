package org.jetbrains.plugins.ruby.testing.testunit.runner;

import com.intellij.execution.Location;
import com.intellij.execution.testframework.*;
import com.intellij.execution.testframework.ui.PrintableTestProxy;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.pom.Navigatable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ruby.testing.testunit.runner.states.*;
import org.jetbrains.plugins.ruby.testing.testunit.runner.ui.TestsPresentationUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author: Roman Chernyatchik
 */
public class RTestUnitTestProxy extends CompositePrintable implements PrintableTestProxy {
  private static final Logger LOG = Logger.getInstance(RTestUnitTestProxy.class.getName());

  private List<RTestUnitTestProxy> myChildren;
  private RTestUnitTestProxy myParent;

  private AbstractState myState = NotRunState.getInstance();
  private String myName;
  private Integer myDuration = null; // duration is unknown
  private boolean myDurationIsCached = false; // is used for separating unknown and unset duration


  private Printer myPrinter = Printer.DEAF;

  private final boolean myIsSuite;

  public RTestUnitTestProxy(final String testName, final boolean isSuite) {
    myName = testName;
    myIsSuite = isSuite;
  }

  public boolean isInProgress() {
    //final RTestUnitTestProxy parent = getParent();

    return myState.isInProgress();
  }

  public boolean isDefect() {
    return myState.isDefect();
  }

  public boolean shouldRun() {
    return true;
  }

  public int getMagnitude() {
    // Is used by some of Tests Filters

    //WARN: It is Hack, see PoolOfTestStates, API is necessary
    //TODO ignored
    return getMagnitudeInfo().getValue();
  }

  public TestStateInfo.Magnitude getMagnitudeInfo() {
    return myState.getMagnitude();
  }

  public boolean isLeaf() {
    return myChildren == null || myChildren.isEmpty();
  }

  public void addChild(final RTestUnitTestProxy child) {
    if (myChildren == null) {
      myChildren = new ArrayList<RTestUnitTestProxy>();
    }
    myChildren.add(child);
    //TODO reset children cache
    child.setParent(this);

    if (myPrinter != Printer.DEAF) {
      child.setPrintLinstener(myPrinter);
      child.fireOnNewPrintable(child);
    }
  }

  public String getName() {
    return myName;
  }

  @Nullable
  public Location getLocation(final Project project) {
    //TODO
    //determines location of test proxy
    return null;
  }

  @Nullable
  public Navigatable getDescriptor(final Location location) {
    //TODO
    // by location gets navigatable element.
    // It can be file or place in file (e.g. when OPEN_FAILURE_LINE is enabled)
    return null;
  }

  public boolean isSuite() {
    return myIsSuite;
  }

  public RTestUnitTestProxy getParent() {
    return myParent;
  }

  public List<? extends RTestUnitTestProxy> getChildren() {
    return myChildren != null ? myChildren : Collections.<RTestUnitTestProxy>emptyList();
  }

  public List<RTestUnitTestProxy> getAllTests() {
    final List<RTestUnitTestProxy> allTests = new ArrayList<RTestUnitTestProxy>();

    allTests.add(this);

    for (RTestUnitTestProxy child : getChildren()) {
      allTests.addAll(child.getAllTests());
    }

    return allTests;
  }


  public void setStarted() {
    myState = !myIsSuite ? TestInProgressState.TEST : new SuiteInProgressState(this);
  }

  /**
   * Calculates and caches duration of test or suite
   * @return null if duration is unknown, otherwise duration value in millisec;
   */
  @Nullable
  public Integer getDuration() {
    // Returns duration value for tests
    // or cached duration for suites
    if (myDurationIsCached || !isSuite()) {
      return myDuration;
    }

    //For suites couns and caches durations of its childs. Also it evaluates partial duration,
    //i.e. if duration is unknown it will be ignored in sumary value.
    //If duration for all children is unknown sumary duration will be also unknown
    myDuration = calcSuiteDuration();
    myDurationIsCached = true;

    return myDuration;
  }

  /**
   * Sets duration of test
   * @param duration In milleseconds
   */
  public void setDuration(final int duration) {
    invalidateCachedDurationForContainerSuites();

    if (!isSuite()) {
      myDurationIsCached = true;
      myDuration = (duration >= 0) ? duration : null;
      return;
    }

    // Not allow to diractly set duration for suites.
    // It should be the sum of children. This requirement is only
    // for safety of current model and may be changed
    LOG.warn("Unsupported operation");
  }

  public void setFinished() {
    if (myState.isFinal()) {
      // we shouldn't fire new printable because final state
      // has been already fired
      return;
    }

    if (!isSuite()) {
      // if isn't in other finished state (ignored, failed or passed)
      myState = TestPassedState.INSTACE;
    } else {
      //Test Suite
      myState = determineSuiteStateOnFinished();
    }
    // prints final state additional info
    fireOnNewPrintable(myState);
  }

  public void setTestFailed(@NotNull final String localizedMessage,
                            @NotNull final String stackTrace, final boolean testError) {
    myState = testError
              ? new TestErrorState(localizedMessage, stackTrace)
              : new TestFailedState(localizedMessage, stackTrace);
    fireOnNewPrintable(myState);
  }

  public void setParent(@Nullable final RTestUnitTestProxy parent) {
    myParent = parent;
  }

  public List<? extends RTestUnitTestProxy> getChildren(@Nullable final Filter filter) {
    final List<? extends RTestUnitTestProxy> allChildren = getChildren();

    if (filter == Filter.NO_FILTER || filter == null) {
      return allChildren;
    }

    final List<RTestUnitTestProxy> selectedChildren = new ArrayList<RTestUnitTestProxy>();
    for (RTestUnitTestProxy child : allChildren) {
      if (filter.shouldAccept(child)) {
        selectedChildren.add(child);
      }
    }

    if ((selectedChildren.isEmpty())) {
      return Collections.<RTestUnitTestProxy>emptyList();
    }
    return selectedChildren;
  }

  public boolean wasLaunched() {
    return myState.wasLaunched();
  }

  public boolean isRoot() {
    return getParent() == null;
  }

  public void setPrintLinstener(final Printer printer) {
    myPrinter = printer;

    if (myChildren == null) {
      return;
    }

    for (ChangingPrintable child : myChildren) {
      child.setPrintLinstener(printer);
    }
  }

  /**
   * Prints this proxy and all it's chidren on ginven printer
   * @param printer Printer
   */
  public void printOn(final Printer printer) {
    super.printOn(printer);
    CompositePrintable.printAllOn(getChildren(), printer);

    //Tests State, that provide and formats additional output
    // (contains stactrace info, ignored tests, etc)
    myState.printOn(printer);
  }

  /**
   * Stores printable information in internal buffer and notifies
   * proxy's printer about new text available
   * @param printable Printable info
   */
  @Override
  public void addLast(final Printable printable) {
    super.addLast(printable);
    fireOnNewPrintable(printable);
  }

  public void addStdOutput(final String output) {
    addLast(new Printable() {
      public void printOn(final Printer printer) {
        printer.print(output, ConsoleViewContentType.NORMAL_OUTPUT);
      }
    });
  }

  public void addStdErr(final String output) {
    addLast(new Printable() {
      public void printOn(final Printer printer) {
        printer.print(output, ConsoleViewContentType.ERROR_OUTPUT);
      }
    });
  }

  public void addSystemOutput(final String output) {
    addLast(new Printable() {
      public void printOn(final Printer printer) {
        printer.print(output, ConsoleViewContentType.SYSTEM_OUTPUT);
      }
    });
  }

  private void fireOnNewPrintable(final Printable printable) {
    myPrinter.onNewAvaliable(printable);
  }

  @NotNull
  public String getPresentableName() {
    return TestsPresentationUtil.getPresentableName(this);
  }

  @Override
  public String toString() {
    return getPresentableName();
  }

  /**
   * Process was terminated
   */
  public void setTerminated() {
    if (myState.isFinal()) {
      return;
    }
    myState = TerminatedState.INSTANCE;
    final List<? extends RTestUnitTestProxy> children = getChildren();
    for (RTestUnitTestProxy child : children) {
      child.setTerminated();
    }
    fireOnNewPrintable(myState);
  }

  public boolean wasTerminated() {
    return myState.wasTerminated();
  }

  /**
   * Check if suite contains error tests or suites
   * @return True if contains
   */
  private boolean containsErrorTests() {
    final List<? extends RTestUnitTestProxy> children = getChildren();
    for (RTestUnitTestProxy child : children) {
      if (child.getMagnitudeInfo() == TestStateInfo.Magnitude.ERROR_INDEX) {
        return true;
      }
    }
    return false;
  }

  /**
   * Determines site state after it has been finished
   * @return New state
   */
  private AbstractState determineSuiteStateOnFinished() {
    final AbstractState state;
    if (isLeaf()) {
      state = SuiteFinishedState.EMPTY_SUITE;
    } else {
      if (isDefect()) {
        // Test suit contains errors if at least one of its tests contains error
        state = !containsErrorTests()
                  ?  SuiteFinishedState.FAILED_SUITE
                  :  SuiteFinishedState.ERROR_SUITE;
      } else {
        state = SuiteFinishedState.PASSED_SUITE;
      }
    }
    return state;
  }

  @Nullable
  private Integer calcSuiteDuration() {
    int partialDuration = 0;
    boolean durationOfChildrenIsUnknown = true;

    for (RTestUnitTestProxy child : getChildren()) {
      final Integer duration = child.getDuration();
      if (duration != null) {
        durationOfChildrenIsUnknown = false;
        partialDuration += duration.intValue();
      }
    }
    // Lets convert partial duration in integer object. Negative partial duration
    // means that duration of all children is unknown
    return durationOfChildrenIsUnknown ? null : partialDuration;
  }

  /**
   * Recursicely invalidates cached duration for container(parent) suites
   */
  private void invalidateCachedDurationForContainerSuites() {
    // Invalidates duration of this suite
    myDuration = null;
    myDurationIsCached = false;

    // Invalidates duration of container suite
    final RTestUnitTestProxy containerSuite = getParent();
    if (containerSuite != null) {
      containerSuite.invalidateCachedDurationForContainerSuites();
    }
  }
}
