package com.jetbrains.python.testing;

import com.jetbrains.python.run.AbstractPythonRunConfigurationParams;

/**
 * User: catherine
 */
public interface AbstractPythonTestRunConfigurationParams {
  AbstractPythonRunConfigurationParams getBaseParams();

  String getClassName();
  void setClassName(String className);

  String getFolderName();
  void setFolderName(String folderName);

  String getScriptName();
  void setScriptName(String scriptName);

  String getMethodName();
  void setMethodName(String methodName);

  AbstractPythonTestRunConfiguration.TestType getTestType();
  void setTestType(AbstractPythonTestRunConfiguration.TestType testType);

}
