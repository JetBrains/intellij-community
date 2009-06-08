/*
 * User: anna
 * Date: 03-Jun-2009
 */
package com.intellij.rt.junit4;

import org.junit.runner.Description;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;

class SMTestSender extends RunListener {
  private String myCurrentClassName;

  public void testRunStarted(Description description) throws Exception {
    //System.out.println("##teamcity[testSuiteStarted name =\'" + description.toString() + "\']");
  }

  public void testRunFinished(Result result) throws Exception {
    if (myCurrentClassName != null) {
      System.out.println("##teamcity[testSuiteFinished name=\'" + myCurrentClassName + "\']");
    }
  }

  public void testStarted(Description description) throws Exception {
    final String className = description.getClassName();
    if (myCurrentClassName == null || !myCurrentClassName.equals(className)) {
      if (myCurrentClassName != null) {
        System.out.println("##teamcity[testSuiteFinished name=\'" + myCurrentClassName + "\']");
      }
      myCurrentClassName = className;
      System.out.println("##teamcity[testSuiteStarted name=\'" + className + "\']");
    }
    System.out.println("##teamcity[testStarted name=\'" + description.getMethodName() + "\']");
  }

  public void testFinished(Description description) throws Exception {
    System.out.println("##teamcity[testFinished name=\'" + description.getMethodName() + "\']");
  }

  public void testFailure(Failure failure) throws Exception {
    System.out.println("##teamcity[testFailed name=\'" +
                       failure.getDescription().getMethodName() +
                       "\' message=\'" +
                       failure.getMessage() +
                       "\' details=\'" +
                       failure.getTrace().replaceAll("\n", "n").replaceAll("\r", "r") +
                       "\']");
  }

  public void testAssumptionFailure(Failure failure) {

  }

  public void testIgnored(Description description) throws Exception {
    System.out.println("##teamcity[testIgnored name=\'" + description.getMethodName() + "\']");
  }
}