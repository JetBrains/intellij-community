package com.jetbrains.python.testing;

import com.jetbrains.python.run.AbstractPythonRunConfigurationParams;

/**
 * @author Leonid Shalupov
 */
public interface PythonUnitTestRunConfigurationParams {
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
}
