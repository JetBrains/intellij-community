// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.rt.testng;


import org.testng.CommandLineArgs;
import org.testng.ITestNGListener;
import org.testng.TestNG;
import org.testng.collections.Lists;
import org.testng.xml.XmlClass;
import org.testng.xml.XmlInclude;
import org.testng.xml.XmlSuite;
import org.testng.xml.XmlTest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class IDEARemoteTestNG extends TestNG {

  private final String myParam;

  public IDEARemoteTestNG(String param) {
    myParam = param;
  }

  private static void calculateAllSuites(List<XmlSuite> suites, List<XmlSuite> outSuites) {
    for (XmlSuite s : suites) {
      outSuites.add(s);
      calculateAllSuites(s.getChildSuites(), outSuites);
    }
  }

  @Override
  public void configure(CommandLineArgs cla) {
    super.configure(cla);
  }

  @Override
  public void run() {
    try {
      initializeSuitesAndJarFile();

      List<XmlSuite> suites = Lists.newArrayList();
      calculateAllSuites(m_suites, suites);
      if (!suites.isEmpty()) {
        for (XmlSuite suite : suites) {
          final List<XmlTest> tests = suite.getTests();
          for (XmlTest test : tests) {
            try {
              if (myParam != null) {
                for (XmlClass aClass : test.getXmlClasses()) {
                  List<XmlInclude> includes = new ArrayList<>();
                  for (XmlInclude include : aClass.getIncludedMethods()) {
                    includes.add(new XmlInclude(include.getName(), Collections.singletonList(Integer.parseInt(myParam)), 0));
                  }
                  aClass.setIncludedMethods(includes);
                }
              }
            }
            catch (NumberFormatException e) {
              System.err.println("Invocation number: expected integer but found: " + myParam);
            }
          }
        }

        attachListeners(new IDEATestNGRemoteListener());
        super.run();
      }
      else {
        System.out.println("##teamcity[enteredTheMatrix]");
        System.err.println("Nothing found to run");
      }
      System.exit(0);
    }
    catch (Throwable cause) {
      cause.printStackTrace(System.err);
      System.exit(-1);
    }
  }

  private static final boolean HAS_ADD_LISTENER_BY_INTERFACE;

  static {
    boolean result = false;
    try {
      TestNG.class.getMethod("addListener", Class.forName("org.testng.ITestNGListener"));
      result = true;
    }
    catch (ReflectiveOperationException ignored) {
    }
    HAS_ADD_LISTENER_BY_INTERFACE = result;
  }

  private void attachListeners(IDEATestNGRemoteListener listener) {
    List<ITestNGListener> listeners = new ArrayList<>();
    listeners.add(getListenerIfExists(() -> new IDEATestNGSuiteListener(listener)));
    listeners.add(getListenerIfExists(() -> new IDEATestNGTestListener(listener)));
    listeners.add(getListenerIfExists(() -> new IDEATestNGConfigurationListener(listener)));
    listeners.add(getListenerIfExists(() -> new IDEATestNGInvokedMethodListener(listener)));

    for (ITestNGListener l : listeners) {
      if (l == null) continue;
      if (HAS_ADD_LISTENER_BY_INTERFACE) {
        addListener(l);
      }
      else {
        addListener((Object)l);
      }
    }

    for (Object l : listeners) {
      if (l instanceof Startable) ((Startable)l).start();
    }
  }

  private static ITestNGListener getListenerIfExists(ThrowableSupplier<ITestNGListener> factory) {
    try {
      return factory.get();
    }
    catch (Throwable ignored) {
      return null;
    }
  }

  private interface ThrowableSupplier<T> {
    T get() throws Throwable;
  }
}
