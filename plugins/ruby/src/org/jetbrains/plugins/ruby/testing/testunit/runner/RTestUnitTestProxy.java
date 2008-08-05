package org.jetbrains.plugins.ruby.testing.testunit.runner;

import com.intellij.execution.Location;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.execution.testframework.*;
import com.intellij.execution.testframework.ui.PrintableTestProxy;
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
  public static final int SKIPPED_INDEX = 0;
  public static final int COMPLETE_INDEX = 1;
  public static final int NOT_RUN_INDEX = 2;
  public static final int RUNNING_INDEX = 3;
  public static final int TERMINATED_INDEX = 4;
  public static final int IGNORED_INDEX = 5;
  public static final int FAILED_INDEX = 6;
  public static final int ERROR_INDEX = 8;
  public static final int PASSED_INDEX = COMPLETE_INDEX;

  private List<RTestUnitTestProxy> myChildren;
  private RTestUnitTestProxy myParent;

  private AbstractState myState = NotRunState.getInstance();
  private String myName;


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

  @Deprecated
  public int getMagnitude() {
    // Is used by some of Tests Filters

    //WARN: It is Hack, see PoolOfTestStates, API is necessary
    //TODO ignored, error
    final AbstractState state = myState;

    if (!state.isFinal()) {
      if (!state.wasLaunched()) {
        return NOT_RUN_INDEX;
      }
      return RUNNING_INDEX;

    } if (state.wasTerminated()) {
      return TERMINATED_INDEX;

    } else if (state.isDefect()) {
      return FAILED_INDEX;
    }
    return PASSED_INDEX;
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
      myState = isLeaf()
                ? SuiteFinishedState.EMPTY_SUITE
                : myState.isDefect() ? SuiteFinishedState.FAILED_SUITE : SuiteFinishedState.PASSED_SUITE;
    }
    // prints final state additional info
    fireOnNewPrintable(myState);
  }

  public void setTestFailed(@NotNull final String localizedMessage,
                            @NotNull final String stackTrace) {
    myState = new TestFailedState(localizedMessage, stackTrace);
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
}
