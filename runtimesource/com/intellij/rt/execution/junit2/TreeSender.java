package com.intellij.rt.execution.junit2;

import com.intellij.rt.execution.junit2.segments.OutputObjectRegistryImpl;
import com.intellij.rt.execution.junit2.segments.Packet;
import com.intellij.rt.execution.junit2.segments.PoolOfDelimiters;
import junit.framework.Test;
import junit.framework.TestSuite;

import java.util.Enumeration;
import java.util.Vector;

public class TreeSender implements PoolOfDelimiters {

  private static void sendNode(Test test, Packet packet) {
    Vector testCases = getTestCasesOf(test);
    packet.addObject(test).addLong(testCases.size());
    for (Enumeration each = testCases.elements(); each.hasMoreElements();)
      sendNode((Test)each.nextElement(), packet);
  }

  private static Vector getTestCasesOf(Test test) {
    Vector testCases = new Vector();
    if (test instanceof TestSuite) {
      TestSuite testSuite = (TestSuite)test;
      for (Enumeration each = testSuite.tests(); each.hasMoreElements();) {
        Object childTest = each.nextElement();
        if (childTest instanceof TestSuite && !((TestSuite)childTest).tests().hasMoreElements()) continue;
        testCases.addElement(childTest);
      }
    }
    return testCases;
  }

  public static void sendSuite(OutputObjectRegistryImpl registry, Test suite) {
    Packet packet = registry.createPacket();
    packet.addString(TREE_PREFIX);
    sendNode(suite, packet);
    packet.addString("\n");
    packet.send();
  }
}
