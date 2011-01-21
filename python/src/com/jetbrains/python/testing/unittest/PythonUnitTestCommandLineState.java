package com.jetbrains.python.testing.unittest;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.ParamsGroup;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.util.text.StringUtil;
import com.jetbrains.python.PythonHelpersLocator;
import com.jetbrains.python.testing.PythonTestCommandLineStateBase;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.jetbrains.python.testing.AbstractPythonTestRunConfiguration.TestType.TEST_FOLDER;

/**
 * @author Leonid Shalupov
 */
public class PythonUnitTestCommandLineState extends
                                            PythonTestCommandLineStateBase {
  private final PythonUnitTestRunConfiguration myConfig;
  private static final String UTRUNNER_PY = "pycharm/utrunner.py";

  public PythonUnitTestCommandLineState(PythonUnitTestRunConfiguration runConfiguration, ExecutionEnvironment env) {
    super(runConfiguration, env);
    myConfig = runConfiguration;
  }

  protected void addTestRunnerParameters(GeneralCommandLine cmd) {
    ParamsGroup script_params = cmd.getParametersList().getParamsGroup(GROUP_SCRIPT);
    assert script_params != null;
    script_params.addParameter(new File(PythonHelpersLocator.getHelpersRoot(), UTRUNNER_PY).getAbsolutePath());
    script_params.addParameters(getTestSpecs());
  }

  @Override
  protected Collection<String> buildPythonPath() {
    List<String> pythonPath = new ArrayList<String>(super.buildPythonPath());
    // the first entry is the helpers path; add script directory as second entry
    if (myConfig.getTestType() == TEST_FOLDER) {
      pythonPath.add(1, myConfig.getFolderName());
    }
    else {
      pythonPath.add(1, new File(myConfig.getWorkingDirectory(), myConfig.getScriptName()).getParent());
    }
    return pythonPath;
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
        if (!StringUtil.isEmpty(myConfig.getPattern())) {
          specs.add(myConfig.getFolderName() + "/" + ";" + myConfig.getPattern());
        }
        else {
          specs.add(myConfig.getFolderName() + "/");
        }
        // TODO[kate]:think about delimiter between folderName and Pattern
        break;
      case TEST_FUNCTION:
        specs.add(myConfig.getScriptName() + "::::" + myConfig.getMethodName());
        break;
      default:
        throw new IllegalArgumentException("Unknown test type: " + myConfig.getTestType());
    }
    specs.add(String.valueOf(myConfig.isPureUnittest()));
    return specs;
  }
}
