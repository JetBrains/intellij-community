// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.rt.testng;


import org.testng.CommandLineArgs;
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
      if(suites.size() > 0) {

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
    catch(Throwable cause) {
      cause.printStackTrace(System.err);
      System.exit(-1);
    }
  }

  private void attachListeners(IDEATestNGRemoteListener listener) {
    addListener((Object)new IDEATestNGSuiteListener(listener));
    addListener((Object)new IDEATestNGTestListener(listener));
    try {
      Class<?> configClass = Class.forName("com.intellij.rt.testng.IDEATestNGConfigurationListener");
      Object configurationListener = configClass.getConstructor(new Class[] {IDEATestNGRemoteListener.class}).newInstance(listener);
      addListener(configurationListener);

      Class<?> invokeClass = Class.forName("com.intellij.rt.testng.IDEATestNGInvokedMethodListener");
      Object invokedMethodListener = invokeClass.getConstructor(new Class[]{IDEATestNGRemoteListener.class}).newInstance(listener);
      addListener(invokedMethodListener);

      //start with configuration started if invoke method listener was not added, otherwise with
      configClass.getMethod("setIgnoreStarted").invoke(configurationListener);
    }
    catch (Throwable ignored) {}
  }
}
