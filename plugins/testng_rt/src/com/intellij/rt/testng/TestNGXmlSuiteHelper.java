// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.rt.testng;

import org.testng.xml.XmlClass;
import org.testng.xml.XmlInclude;
import org.testng.xml.XmlSuite;
import org.testng.xml.XmlTest;
import org.testng.xml.internal.Parser;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class TestNGXmlSuiteHelper {
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
                                Logger logger,
                                boolean requireHttp) {
    XmlSuite xmlSuite = new XmlSuite();
    xmlSuite.setParameters(testParams);
    return writeSuite(map, name, rootPath, logger, requireHttp, xmlSuite);
  }

  public static File writeSuite(Map<String, Map<String, List<String>>> map,
                                String name,
                                String rootPath,
                                Logger logger,
                                boolean requireHttp,
                                XmlSuite xmlSuite) {
    XmlTest xmlTest = new XmlTest(xmlSuite);
    xmlTest.setName(name);
    List<XmlClass> xmlClasses = new ArrayList<>();
    int idx = 0;
    for (String className : map.keySet()) {
      final XmlClass xmlClass = new XmlClass(className, idx++, false);
      final Map<String, List<String>> collection = map.get(className);
      if (collection != null) {
        final ArrayList<XmlInclude> includedMethods = new ArrayList<>();
        int mIdx = 0;
        for (String methodName : collection.keySet()) {
          final List<Integer> includes = new ArrayList<>();
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
    File xmlFile = new File(rootPath, "temp-testng-customsuite.xml");
    String toXml = xmlSuite.toXml();
    if (requireHttp) {
      String target = "https://testng.org/" + Parser.TESTNG_DTD;
      int dtdIdx = toXml.indexOf(target);
      if (dtdIdx > 0) {
        toXml = toXml.substring(0, dtdIdx) + org.testng.xml.internal.Parser.TESTNG_DTD_URL + toXml.substring(dtdIdx + target.length());
      }
    }
    writeToFile(logger, xmlFile, toXml);
    return xmlFile;
  }

  public static void writeToFile(Logger logger, File xmlFile, String content) {
    try {
      try (OutputStream stream = new FileOutputStream(xmlFile, false)) {
        byte[] text = content.getBytes(StandardCharsets.UTF_8);
        stream.write(text, 0, text.length);
      }
    }
    catch (IOException e) {
      logger.log(e);
    }
  }
}
