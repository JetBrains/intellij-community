package org.jetbrains.plugins.ruby.testing.testunit.runner.model;

import com.intellij.execution.Location;
import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.execution.testframework.Filter;
import com.intellij.openapi.project.Project;
import com.intellij.pom.Navigatable;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author: Roman Chernyatchik
 */
public class RTestUnitTestProxy implements AbstractTestProxy {
  private List<RTestUnitTestProxy> myChildren;
  private RTestUnitTestProxy myParent;

  private PassState myState = PassState.NOT_RUN;
  private String myName;

  private Boolean isDefectWasReallyFound = null; // null - is unset

  public RTestUnitTestProxy(final String testName) {
    myName = testName;
  }

  public boolean isInProgress() {
    //final RTestUnitTestProxy parent = getParent();

    return myState == PassState.STARTED;
  }

  public boolean isDefect() {
    if (isDefectWasReallyFound != null) {
      return isDefectWasReallyFound.booleanValue();
    }

    // didn't pass
    if (myState == PassState.FAILED) {
      isDefectWasReallyFound = true;
      return true;
    }

    //passed
    if (myState == PassState.PASSED) {
      isDefectWasReallyFound = false;
      return false;
    }

    if (isLeaf()) {
      // test in progress
      return false;
    }

    // Test suit fails if any of its tests fails
    final List<? extends RTestUnitTestProxy> children = getChildren();
    for (RTestUnitTestProxy child : children) {
      if (child.isDefect()) {
        isDefectWasReallyFound = true;
        return true;
      }
    }

    if (myState == PassState.SUITE_FINISHED) {
      //Test suite finished, we can cache result
      isDefectWasReallyFound = false;
    }
    return false;
  }

  public boolean shouldRun() {
    return true;
  }

  public int getMagnitude() {
    //TODO[romeo] what it is?
    return 0;
  }

  public boolean isLeaf() {
    return myChildren == null || myChildren.isEmpty();
  }

  public void addChild(final RTestUnitTestProxy proxy) {
    if (myChildren == null) {
      myChildren = new ArrayList<RTestUnitTestProxy>();
    }
    myChildren.add(proxy);
    proxy.setParent(this);
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
    myState = PassState.STARTED;
  }

  public void setFinished() {
    if (isLeaf()) {
      // if isn't in other finished state (ignored, failed or passed)
      if (myState == PassState.NOT_RUN || myState == PassState.STARTED) {
        myState = PassState.PASSED;
      }
    } else {
      //Test Suite
      myState = PassState.SUITE_FINISHED;
    }
  }

  public void setFailed() {
    myState = PassState.FAILED;
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

  /**
   * TODO[romeo] if wan't run is it deffect or not?
   * @return
   */
  public boolean wasRun() {
    return myState != PassState.NOT_RUN;
  }
}
