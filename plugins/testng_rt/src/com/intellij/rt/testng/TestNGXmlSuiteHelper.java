// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.rt.testng;

import org.testng.xml.XmlClass;
import org.testng.xml.XmlInclude;
import org.testng.xml.XmlSuite;
import org.testng.xml.XmlTest;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TestNGXmlSuiteHelper {
  public interface Logger {
    Logger DEAF = new Logger() {
      @Override
      public void log(Throwable e) {}
    };

    void log(Throwable e);
  }
  
  public static File writeSuite(Map<String, Map<String, List<String>>> map, 
                                Map<String, String> testParams, 
                                String name,
                                String rootPath,
                                Logger logger) {
    File xmlFile;
    final XmlSuite xmlSuite = new XmlSuite();
    xmlSuite.setParameters(testParams);
    XmlTest xmlTest = new XmlTest(xmlSuite);
    xmlTest.setName(name);
    List<XmlClass> xmlClasses = new ArrayList<XmlClass>();
    int idx = 0;
    for (String className : map.keySet()) {
      final XmlClass xmlClass = new XmlClass(className, idx++, false);
      final Map<String, List<String>> collection = map.get(className);
      if (collection != null) {
        final ArrayList<XmlInclude> includedMethods = new ArrayList<XmlInclude>();
        int mIdx = 0;
        for (String methodName : collection.keySet()) {
          final List<Integer> includes = new ArrayList<Integer>();
          for (String include : collection.get(methodName)) {
            try {
              includes.add(Integer.parseInt(include));
            }
            catch (NumberFormatException e) {
              logger.log(e);
            }
          }
          includedMethods.add(new XmlInclude(methodName, includes, mIdx++));
        }
        xmlClass.setIncludedMethods(includedMethods);
      }
      xmlClasses.add(xmlClass);
    }
    xmlTest.setXmlClasses(xmlClasses);
    xmlFile = new File(rootPath, "temp-testng-customsuite.xml");
    final String toXml = xmlSuite.toXml();
    writeToFile(logger, xmlFile, toXml);
    return xmlFile;
  }

  public static void writeToFile(Logger logger, File xmlFile, String content) {
    try {
      OutputStream stream = new FileOutputStream(xmlFile, false);
      try {
        byte[] text = content.getBytes("UTF-8");
        stream.write(text, 0, text.length);
      }
      finally {
        stream.close();
      }
    }
    catch (IOException e) {
      logger.log(e);
    }
  }
}
