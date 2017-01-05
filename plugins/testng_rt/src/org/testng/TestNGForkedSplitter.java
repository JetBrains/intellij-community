/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.testng;

import com.intellij.rt.execution.testFrameworks.ForkedByModuleSplitter;

import java.io.File;
import java.io.PrintStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TestNGForkedSplitter extends ForkedByModuleSplitter {


  public TestNGForkedSplitter(String workingDirsPath, List newArgs) {
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
                                   List classNames,
                                   String packageName,
                                   String workingDir,
                                   String classpath,
                                   String repeatCount, int result) throws Exception {
    final LinkedHashMap<String, Map<String, List<String>>> classes = new LinkedHashMap<String, Map<String, List<String>>>();
    for (Object className : classNames) {
      classes.put((String)className, null);
    }

    String rootPath = null;
    if (!myNewArgs.isEmpty()) {
      rootPath = new File((String)myNewArgs.get(0)).getParent();
    }

    final File file =
      TestNGXmlSuiteHelper.writeSuite(classes, new LinkedHashMap<String, String>(), moduleName, rootPath, TestNGXmlSuiteHelper.Logger.DEAF);
    file.deleteOnExit();

    return Math.min(result, startChildFork(Collections.singletonList(file.getAbsolutePath()), new File(workingDir), classpath, repeatCount));
  }

}
