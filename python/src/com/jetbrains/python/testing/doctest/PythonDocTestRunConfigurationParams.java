package com.jetbrains.python.testing.doctest;

import com.jetbrains.python.run.AbstractPythonRunConfigurationParams;

/**
 * @author Leonid Shalupov
 */
public interface PythonDocTestRunConfigurationParams {
  AbstractPythonRunConfigurationParams getBaseParams();

  String getClassName();
  void setClassName(String className);

  String getFolderName();
  void setFolderName(String folderName);

  String getScriptName();
  void setScriptName(String scriptName);

  String getMethodName();
  void setMethodName(String methodName);

  PythonDocTestRunConfiguration.TestType getTestType();
  void setTestType(PythonDocTestRunConfiguration.TestType testType);

  String getPattern();
  void setPattern(String pattern);

}
