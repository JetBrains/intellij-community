package com.intellij.rt.execution.junit2;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestResult;
import junit.framework.TestSuite;

import java.util.Hashtable;

public class RunOnce extends TestResult {
  private Hashtable myPeformedTests = new Hashtable();
  private static final String NOT_ALLOWED_IN_ID = ":";

  protected void run(TestCase test) {
    if (test.getClass().getName().startsWith(TestSuite.class.getName())) {
      super.run(test);
    } else {
      String testKey = keyOf(test);
      if (!myPeformedTests.containsKey(testKey)) {
        super.run(test);
        myPeformedTests.put(testKey, test);
      } else {
        fireTestSkipped(test, (Test)myPeformedTests.get(testKey));
      }
    }
  }

  private void fireTestSkipped(TestCase test, Test peformedTest) {
    for (int i = 0; i < fListeners.size(); i++) {
      Object listener = fListeners.get(i);
      if (listener instanceof TestSkippingListener) {
        ((TestSkippingListener)listener).onTestSkipped(test, peformedTest);
      }
    }
  }

  private String keyOf(TestCase test) {
    return test.getClass().getName() + NOT_ALLOWED_IN_ID +
           test.getName() + NOT_ALLOWED_IN_ID +
           test.toString();
  }
}
