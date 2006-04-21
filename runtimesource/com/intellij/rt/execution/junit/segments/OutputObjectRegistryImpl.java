package com.intellij.rt.execution.junit.segments;

import com.intellij.rt.execution.junit.TestAllInPackage2;
import com.intellij.rt.execution.junit.JUnit4API;
import com.intellij.rt.execution.junit.TestRunnerUtil;
import junit.extensions.TestDecorator;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import java.util.Hashtable;

public class OutputObjectRegistryImpl implements OutputObjectRegistry, PacketFactory {
  private Hashtable myKnownKeys = new Hashtable();
  private int myLastIndex = 0;
  private PacketProcessor myMainTransport;
  private final JUnit4API JUnit4API;
  private PacketProcessor myAuxilaryTransport;

  public OutputObjectRegistryImpl(PacketProcessor transport, final JUnit4API isJUnit4) {
    myMainTransport = transport;
    this.JUnit4API = isJUnit4;
  }

  public OutputObjectRegistryImpl(PacketProcessor mainTransport, PacketProcessor auxilaryTransport, JUnit4API isJUnit4) {
    this(mainTransport, isJUnit4);
    myAuxilaryTransport = auxilaryTransport;
  }

  public String referenceTo(Test test) {
    while (test instanceof TestDecorator) {
      test = ((TestDecorator)test).getTest();
    }
    if (myKnownKeys.containsKey(test))
      return (String) myKnownKeys.get(test);
    return sendObject(test);
  }

  public Packet createPacket() {
    return new Packet(myMainTransport, this);
  }

  private String sendObject(Test test) {
    String key = String.valueOf(myLastIndex++);
    myKnownKeys.put(test, key);
    Packet packet = createPacket().addString(PoolOfDelimiters.OBJECT_PREFIX).addReference(key);
    addStringRepresentation(test, packet);
    packet.addLong(test.countTestCases());
    sendViaAllTransports(packet);
    return key;
  }

  private void sendViaAllTransports(Packet packet) {
    packet.send();
    if (myAuxilaryTransport != null)
      packet.sendThrough(myAuxilaryTransport);
  }

  private void addStringRepresentation(Test test, Packet packet) {
    if (JUnit4API != null && JUnit4API.isJUnit4TestMethodAdapter(test)) {
      addTestMethod(packet, ((TestCase)test).getName(), JUnit4API.getJUnit4MethodAdapterClassName(test));
    }
    else if (test instanceof TestRunnerUtil.FailedTestCase) {
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

  private static void addTestClass(Packet packet, String className) {
    packet.
        addLimitedString(PoolOfTestTypes.TEST_CLASS).
        addLimitedString(className);
  }

  private static void addUnknownTest(Packet packet, Test test) {
    packet.
        addLimitedString(PoolOfTestTypes.UNKNOWN).
        addLong(test.countTestCases()).
        addLimitedString(test.getClass().getName());
  }

  private static void addAllInPackage(Packet packet, String name) {
    packet.
        addLimitedString(PoolOfTestTypes.ALL_IN_PACKAGE).
        addLimitedString(name);
  }

  private static void addTestMethod(Packet packet, String methodName, String className) {
    packet.
        addLimitedString(PoolOfTestTypes.TEST_METHOD).
        addLimitedString(methodName).
        addLimitedString(className);
  }

  public void forget(Test test) {
    myKnownKeys.remove(test);
  }
}
