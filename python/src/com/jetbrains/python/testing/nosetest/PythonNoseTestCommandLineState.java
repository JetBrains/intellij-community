package com.jetbrains.python.testing.nosetest;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.ParamsGroup;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.jetbrains.python.PythonHelpersLocator;
import com.jetbrains.python.testing.PythonTestCommandLineStateBase;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * User: catherine
 */
public class PythonNoseTestCommandLineState extends PythonTestCommandLineStateBase {
  private final PythonNoseTestRunConfiguration myConfig;
  private static final String NOSERUNNER_PY = "pycharm/noserunner.py";

  public PythonNoseTestCommandLineState(PythonNoseTestRunConfiguration runConfiguration, ExecutionEnvironment env) {
    super(runConfiguration, env);
    myConfig = runConfiguration;
  }

  protected void addTestRunnerParameters(GeneralCommandLine cmd) {
    ParamsGroup script_params = cmd.getParametersList().getParamsGroup(GROUP_SCRIPT);
    assert script_params != null;
    script_params.addParameter(new File(PythonHelpersLocator.getHelpersRoot(), NOSERUNNER_PY).getAbsolutePath());
    script_params.addParameters(getTestSpecs());
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
	      specs.add(myConfig.getFolderName() + "/");
        break;
      case TEST_FUNCTION:
        specs.add(myConfig.getScriptName() + "::::" + myConfig.getMethodName());
        break;
      default:
        throw new IllegalArgumentException("Unknown test type: " + myConfig.getTestType());
    }
    if (!myConfig.getParams().isEmpty())
        specs.add(myConfig.getParams());
    return specs;
  }
}
