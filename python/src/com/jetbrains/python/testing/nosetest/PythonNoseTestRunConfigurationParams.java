package com.jetbrains.python.testing.nosetest;

import com.jetbrains.python.run.AbstractPythonRunConfigurationParams;
import com.jetbrains.python.testing.PythonUnitTestRunConfiguration;

/**
 * User: catherine
 */
public interface PythonNoseTestRunConfigurationParams {
  AbstractPythonRunConfigurationParams getBaseParams();

  String getClassName();
  void setClassName(String className);

  String getFolderName();
  void setFolderName(String folderName);

  String getScriptName();
  void setScriptName(String scriptName);

  String getMethodName();
  void setMethodName(String methodName);

  PythonUnitTestRunConfiguration.TestType getTestType();
  void setTestType(PythonUnitTestRunConfiguration.TestType testType);

  String getParams();
  void setParams(String params);
}
