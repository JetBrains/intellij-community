package com.intellij.rt.execution.junit2.segments;

import com.intellij.rt.execution.junit.TestAllInPackage2;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import junit.extensions.TestDecorator;

import java.util.Hashtable;

public class OutputObjectRegistryImpl implements OutputObjectRegistry, PacketFactory, PoolOfDelimiters {
  private Hashtable myKnownKeys = new Hashtable();
  private int myLastIndex = 0;
  private PacketProcessor myMainTransport;
  private PacketProcessor myAuxilaryTransport;

  public OutputObjectRegistryImpl(PacketProcessor transport) {
    myMainTransport = transport;
  }

  public OutputObjectRegistryImpl(PacketProcessor mainTransport, PacketProcessor auxilaryTransport) {
    myMainTransport = mainTransport;
    myAuxilaryTransport = auxilaryTransport;
  }

  public String referenceTo(Test object) {
    while (object instanceof TestDecorator) {
      object = ((TestDecorator)object).getTest();
    }
    if (myKnownKeys.containsKey(object))
      return (String) myKnownKeys.get(object);
    return sendObject(object);
  }

  public Packet createPacket() {
    return new Packet(myMainTransport, this);
  }

  private String sendObject(Test test) {
    String key = String.valueOf(myLastIndex++);
    myKnownKeys.put(test, key);
    Packet packet = createPacket().addString(OBJECT_PREFIX).addReference(key);
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

  private static void addStringRepresentation(Test test, Packet packet) {
    if (test instanceof TestCase) {
      addTestMethod(test, packet);
    } else if (test instanceof TestAllInPackage2) {
      TestAllInPackage2 allInPackage = (TestAllInPackage2)test;
      addAllInPackage(packet, allInPackage.getName());
    } else if (test instanceof TestSuite) {
      TestSuite testSuite = (TestSuite)test;
      String fullName = testSuite.getName();
      if (fullName == null) {
        addUnknownTest(packet, test);
        return;
      }
      addTestClass(packet, fullName);
    } else
      addUnknownTest(packet, test);
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

  private static void addTestMethod(Test test, Packet packet) {
    TestCase testCase = (TestCase)test;
    packet.
        addLimitedString(PoolOfTestTypes.TEST_METHOD).
        addLimitedString(testCase.getName()).
        addLimitedString(testCase.getClass().getName());
  }

  public void forget(Test test) {
    myKnownKeys.remove(test);
  }
}
