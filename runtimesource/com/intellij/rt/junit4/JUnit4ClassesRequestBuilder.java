/*
 * User: anna
 * Date: 11-Jun-2009
 */
package com.intellij.rt.junit4;

import org.junit.runner.Request;

public class JUnit4ClassesRequestBuilder {
  public static Request getClassesRequest(String suiteName, Class[] classes) {
    try {
      return (Request)Class.forName("org.junit.internal.requests.ClassesRequest")
                    .getConstructor(new Class[]{String.class, new Class[0].getClass()})
                    .newInstance(new Object[]{suiteName, classes});
    }
    catch (Exception e) {
      e.printStackTrace();
      return null;
    }
  }
}