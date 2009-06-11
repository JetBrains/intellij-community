/*
 * User: anna
 * Date: 05-Jun-2009
 */
package com.intellij.rt.junit4;

import com.intellij.rt.execution.junit.segments.OutputObjectRegistryEx;
import com.intellij.rt.execution.junit.segments.Packet;
import com.intellij.rt.execution.junit.segments.PacketProcessor;
import org.junit.runner.Description;

import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class JUnit4OutputObjectRegistry extends OutputObjectRegistryEx {
  public JUnit4OutputObjectRegistry(PacketProcessor mainTransport, PacketProcessor auxilaryTransport) {
    super(mainTransport, auxilaryTransport);
  }

  protected int getTestCont(Object test) {
    return ((Description)test).testCount();
  }

  protected void addStringRepresentation(Object obj, Packet packet) {
    Description test = (Description)obj;
    if (test.isTest()) {
      addTestMethod(packet, getMethodName(test), getClassName(test));
    }
    else if (test.isSuite()) {
      String fullName = getClassName(test);
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

  public static String getClassName(Description description) {
    try {
      return description.getClassName();
    }
    catch (NoSuchMethodError e) {
      final String displayName = description.getDisplayName();
      Matcher matcher = Pattern.compile("(.*)\\((.*)\\)").matcher(displayName);
      return matcher.matches() ? matcher.group(2) : displayName;
    }
  }

  public static String getMethodName(Description description) {
    try {
      return description.getMethodName();
    }
    catch (NoSuchMethodError e) {
      final String displayName = description.getDisplayName();
      Matcher matcher = Pattern.compile("(.*)\\((.*)\\)").matcher(displayName);
      if (matcher.matches()) return matcher.group(1);
      return null;
    }
  }
}