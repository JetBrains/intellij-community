package com.jetbrains.python.testing.doctest;

import com.intellij.execution.runners.ExecutionEnvironment;
import com.jetbrains.python.testing.PythonTestCommandLineStateBase;

import java.util.ArrayList;
import java.util.List;

/**
 * User: catherine
 */
public class PythonDocTestCommandLineState extends PythonTestCommandLineStateBase {
  private final PythonDocTestRunConfiguration myConfig;
  private static final String UTRUNNER_PY = "pycharm/docrunner.py";

  public PythonDocTestCommandLineState(PythonDocTestRunConfiguration runConfiguration, ExecutionEnvironment env) {
    super(runConfiguration, env);
    myConfig = runConfiguration;
  }

  @Override
  protected String getRunner() {
    return UTRUNNER_PY;
  }

  protected List<String> getTestSpecs() {
    List<String> specs = new ArrayList<String>();

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
	if (!myConfig.getPattern().isEmpty())
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
