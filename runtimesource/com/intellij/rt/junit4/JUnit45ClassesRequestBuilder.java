/*
 * User: anna
 * Date: 11-Jun-2009
 */
package com.intellij.rt.junit4;

import org.junit.runner.Request;

public class JUnit45ClassesRequestBuilder {
  public static Request getClassesRequest(String suiteName, Class[] classes) {
    try {
      return Request.runner(new IdeaSuite(new org.junit.internal.builders.AllDefaultPossibilitiesBuilder(true), classes, suiteName));
    }
    catch (Exception initializationError) {
      initializationError.printStackTrace();
      return null;
    }
  }

  
}