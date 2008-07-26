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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author: Roman Chernyatchik
 */
public class RTestUnitTestProxy extends CompositePrintable implements PrintableTestProxy { 
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
    //TODO[romeo] what it is?
    return 0;
  }

  public boolean isLeaf() {
    return myChildren == null || myChildren.isEmpty();
  }

  public void addChild(final RTestUnitTestProxy child) {
    if (myChildren == null) {
      myChildren = new ArrayList<RTestUnitTestProxy>();
    }
    myChildren.add(child);
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
    return null;
  }

  @Nullable
  public Navigatable getDescriptor(final Location location) {
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
    if (!isSuite()) {
      // if isn't in other finished state (ignored, failed or passed)
      if (myState.isFinal()) {
        // we shouldn't fire new printable because final state
        // has been already fired
        return;
      }
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

  public void setParent(final RTestUnitTestProxy parent) {
    myParent = parent;
  }

  public List<? extends RTestUnitTestProxy> getChildren(final Filter filter) {
    if (filter == Filter.NO_FILTER) {
      return getChildren();
    }
    //TODO[romeo] add filter
    return Collections.<RTestUnitTestProxy>emptyList();
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

  @Override
  public String toString() {
    return getName(); 
  }

  private void fireOnNewPrintable(final Printable printable) {
    myPrinter.onNewAvaliable(printable);
  }
}
