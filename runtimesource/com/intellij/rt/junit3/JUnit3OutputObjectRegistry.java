/*
 * User: anna
 * Date: 05-Jun-2009
 */
package com.intellij.rt.junit3;

import com.intellij.rt.execution.junit.segments.OutputObjectRegistryEx;
import com.intellij.rt.execution.junit.segments.Packet;
import com.intellij.rt.execution.junit.segments.PacketProcessor;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

public class JUnit3OutputObjectRegistry extends OutputObjectRegistryEx {
  public JUnit3OutputObjectRegistry(PacketProcessor mainTransport, PacketProcessor auxilaryTransport) {
    super(mainTransport, auxilaryTransport);
  }

  public JUnit3OutputObjectRegistry(PacketProcessor out) {
    super(out);
  }

  protected int getTestCont(Object test) {
    return ((Test)test).countTestCases();
  }

  protected void addStringRepresentation(Object test, Packet packet) {
    if (test instanceof TestRunnerUtil.FailedTestCase) {
      addTestMethod(packet, ((TestRunnerUtil.FailedTestCase)test).getMethodName(), ((TestCase)test).getName());
    }
    else if (test instanceof TestCase) {
      addTestMethod(packet, ((TestCase)test).getName(), test.getClass().getName());
    }
    else if (test instanceof TestAllInPackage2) {
      TestAllInPackage2 allInPackage = (TestAllInPackage2)test;
      addAllInPackage(packet, allInPackage.getName());
    }
    else if (test instanceof TestSuite) {
      TestSuite testSuite = (TestSuite)test;
      String fullName = testSuite.getName();
      if (fullName == null) {
        addUnknownTest(packet, test);
        return;
      }
      addTestClass(packet, fullName);
    }
    else {
      addUnknownTest(packet, test);
    }
  }
}