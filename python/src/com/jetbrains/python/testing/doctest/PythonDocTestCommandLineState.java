// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.testing.doctest;

import com.intellij.execution.runners.ExecutionEnvironment;
import com.jetbrains.python.PythonHelper;
import com.jetbrains.python.testing.PythonTestCommandLineStateBase;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class PythonDocTestCommandLineState extends PythonTestCommandLineStateBase {
  private final PythonDocTestRunConfiguration myConfig;


  public PythonDocTestCommandLineState(PythonDocTestRunConfiguration runConfiguration, ExecutionEnvironment env) {
    super(runConfiguration, env);
    myConfig = runConfiguration;
  }

  @Override
  protected PythonHelper getRunner() {
    return PythonHelper.DOCSTRING;
  }

  @Override
  @NotNull
  protected List<String> getTestSpecs() {
    List<String> specs = new ArrayList<>();

    switch (myConfig.getTestType()) {
      case TEST_SCRIPT:
        specs.add(myConfig.getScriptName());
        break;
      case TEST_CLASS:
        specs.add(myConfig.getScriptName() + "::" + myConfig.getClassName());
        break;
      case TEST_METHOD:
        specs.add(myConfig.getScriptName() + "::" + myConfig.getClassName() + "::" + myConfig.getMethodName());
        break;
      case TEST_FOLDER:
	if (myConfig.usePattern() && !myConfig.getPattern().isEmpty())
          specs.add(myConfig.getFolderName() + "/" + ";" + myConfig.getPattern());
        else
          specs.add(myConfig.getFolderName() + "/");
          // TODO[kate]:think about delimiter between folderName and Pattern
        break;
      case TEST_FUNCTION:
        specs.add(myConfig.getScriptName() + "::::" + myConfig.getMethodName());
        break;
      default:
        throw new IllegalArgumentException("Unknown test type: " + myConfig.getTestType());
    }

    return specs;
  }
}
