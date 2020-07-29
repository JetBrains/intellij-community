// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.rt.testng;

import com.intellij.rt.execution.testFrameworks.ForkedByModuleSplitter;

import java.io.File;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TestNGForkedSplitter extends ForkedByModuleSplitter {


  public TestNGForkedSplitter(String workingDirsPath, List<String> newArgs) {
    super(workingDirsPath, "none", newArgs);
  }

  @Override
  protected String getStarterName() {
    return TestNGForkedStarter.class.getName();
  }

  @Override
  protected int startSplitting(String[] args,
                               String configName, String repeatCount) throws Exception {
    return splitPerModule(repeatCount);
  }

  @Override
  protected int startPerModuleFork(String moduleName,
                                   List<String> classNames,
                                   String packageName,
                                   String workingDir,
                                   String classpath,
                                   List<String> moduleOptions,
                                   String repeatCount, int result, final String filters) throws Exception {
    final LinkedHashMap<String, Map<String, List<String>>> classes = new LinkedHashMap<String, Map<String, List<String>>>();
    for (Object className : classNames) {
      classes.put((String)className, null);
    }

    String rootPath = null;
    if (!myNewArgs.isEmpty()) {
      rootPath = new File(myNewArgs.get(0)).getParent();
    }

    final File file =
      TestNGXmlSuiteHelper.writeSuite(classes, new LinkedHashMap<String, String>(), moduleName, rootPath, TestNGXmlSuiteHelper.Logger.DEAF);
    file.deleteOnExit();

    return Math.min(result, startChildFork(Collections.singletonList(file.getAbsolutePath()), new File(workingDir), classpath,
                                           moduleOptions,
                                           repeatCount));
  }

}
