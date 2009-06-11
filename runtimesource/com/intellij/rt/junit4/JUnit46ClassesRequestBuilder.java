/*
 * User: anna
 * Date: 11-Jun-2009
 */
package com.intellij.rt.junit4;

import org.junit.runner.Computer;
import org.junit.runner.Request;
import org.junit.runners.Suite;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.RunnerBuilder;

public class JUnit46ClassesRequestBuilder {
  
  public static Request getClassesRequest(final String suiteName, Class[] classes) {
    return Request.classes(new Computer() {
      public Suite getSuite(final RunnerBuilder builder, Class[] classes) throws InitializationError {
        return new IdeaSuite(builder, classes, suiteName);
      }
    }, classes);
  }

}