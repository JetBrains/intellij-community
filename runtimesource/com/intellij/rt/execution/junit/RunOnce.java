package com.intellij.rt.execution.junit;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestResult;
import junit.framework.TestSuite;

import java.util.Hashtable;
import java.util.List;
import java.lang.reflect.Field;

public class RunOnce extends TestResult {
  private final Hashtable myPeformedTests = new Hashtable();
  private static final String NOT_ALLOWED_IN_ID = ":";

  protected void run(TestCase test) {
    if (test.getClass().getName().startsWith(TestSuite.class.getName())) {
      super.run(test);
    }
    else {
      String testKey = keyOf(test);
      if (!myPeformedTests.containsKey(testKey)) {
        super.run(test);
        myPeformedTests.put(testKey, test);
      }
      else {
        fireTestSkipped(test, (Test)myPeformedTests.get(testKey));
      }
    }
  }

  private void fireTestSkipped(TestCase test, Test peformedTest) {
    List listeners;
    try {
      Field listenersField = getClass().getSuperclass().getDeclaredField("fListeners");
      listenersField.setAccessible(true);
      listeners = (List)listenersField.get(this);
    }
    catch (Exception e) {
      throw new RuntimeException(e.toString());
    }

    for (int i = 0; i < listeners.size(); i++) {
      Object listener = listeners.get(i);
      if (listener instanceof TestSkippingListener) {
        ((TestSkippingListener)listener).onTestSkipped(test, peformedTest);
      }
    }
  }

  private static String keyOf(TestCase test) {
    return test.getClass().getName() + NOT_ALLOWED_IN_ID + test.getName() + NOT_ALLOWED_IN_ID + test.toString();
  }
}
