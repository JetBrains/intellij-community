package com.jetbrains.python.testing;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.jetbrains.python.PythonHelpersLocator;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Leonid Shalupov
 */
public class PythonUnitTestCommandLineState extends PythonTestCommandLineStateBase {
  private PythonUnitTestRunConfiguration myConfig;
  private static final String UTRUNNER_PY = "pycharm/utrunner.py";

  public PythonUnitTestRunConfiguration getConfig() {
    return myConfig;
  }

  public PythonUnitTestCommandLineState(PythonUnitTestRunConfiguration runConfiguration, ExecutionEnvironment env) {
    super(runConfiguration, env);
    myConfig = runConfiguration;
  }

  protected void addTestRunnerParameters(GeneralCommandLine cmd) {
    cmd.addParameter(new File(PythonHelpersLocator.getHelpersRoot(), UTRUNNER_PY).getAbsolutePath());
    for (String testSpec : getTestSpecs()) {
      cmd.addParameter(testSpec);
    }
  }

  private List<String> getTestSpecs() {
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
        specs.add(myConfig.getFolderName() + "/");
        break;
      default:
        throw new IllegalArgumentException("Unknown test type: " + myConfig.getTestType());
    }

    return specs;
  }
}
