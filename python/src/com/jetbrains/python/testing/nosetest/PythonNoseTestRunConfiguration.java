package com.jetbrains.python.testing.nosetest;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.*;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizerUtil;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.io.FileUtil;
import com.jetbrains.python.run.AbstractPythonRunConfiguration;
import com.jetbrains.python.testing.PythonUnitTestRunConfiguration;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

/**
 * User: catherine
 */
public class PythonNoseTestRunConfiguration extends PythonUnitTestRunConfiguration
                                    implements PythonNoseTestRunConfigurationParams {
  private String myClassName = "";
  private String myScriptName = "";
  private String myMethodName = "";
  private String myFolderName = "";
  private TestType myTestType = TestType.TEST_SCRIPT;
  private String myParams = ""; // parameters for nosetests

  protected PythonNoseTestRunConfiguration(RunConfigurationModule module,
                                     ConfigurationFactory configurationFactory, String name) {
    super(module, configurationFactory, name);
  }

  @Override
  protected ModuleBasedConfiguration createInstance() {
    return new PythonNoseTestRunConfiguration(getConfigurationModule(), getFactory(), getName());
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    super.readExternal(element);
    myParams = JDOMExternalizerUtil.readField(element, "PARAMS");
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    super.writeExternal(element);
    JDOMExternalizerUtil.writeField(element, "PARAMS", myParams);
  }

  @Override
  public SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
    return new PythonNoseTestRunConfigurationEditor(getProject(), this);
  }

  public String getParams() {
    return myParams;
  }

  public void setParams(String pattern) {
    myParams = pattern;
  }

  public RunProfileState getState(@NotNull final Executor executor, @NotNull final ExecutionEnvironment env) throws ExecutionException {
    return new PythonNoseTestCommandLineState(this, env);
  }
  public String getClassName() {
    return myClassName;
  }

  public void setClassName(String className) {
    myClassName = className;
  }

  public String getFolderName() {
    return myFolderName;
  }

  public void setFolderName(String folderName) {
    myFolderName = folderName;
  }

  public String getScriptName() {
    return myScriptName;
  }

  public void setScriptName(String scriptName) {
    myScriptName = scriptName;
  }

  public String getMethodName() {
    return myMethodName;
  }

  public void setMethodName(String methodName) {
    myMethodName = methodName;
  }

  public TestType getTestType() {
    return myTestType;
  }

  public void setTestType(TestType testType) {
    myTestType = testType;
  }
  @Override
  public String suggestedName() {
    switch (myTestType) {
      case TEST_CLASS:
        return "Nosetests in " + myClassName;
      case TEST_METHOD:
        return "Nosetests " + myClassName + "." + myMethodName;
      case TEST_SCRIPT:
        return "Nosetests in " + myScriptName;
      case TEST_FOLDER:
        return "Nosetests in " + FileUtil.toSystemDependentName(myFolderName);
      case TEST_FUNCTION:
        return "Nosetests " + myMethodName;
      default:
        throw new IllegalStateException("Unknown test type: " + myTestType);
    }
  }

  public static void copyParams(PythonNoseTestRunConfigurationParams source, PythonNoseTestRunConfigurationParams target) {
    AbstractPythonRunConfiguration.copyParams(source.getBaseParams(), target.getBaseParams());
    target.setScriptName(source.getScriptName());
    target.setClassName(source.getClassName());
    target.setFolderName(source.getFolderName());
    target.setMethodName(source.getMethodName());
    target.setTestType(source.getTestType());
    target.setParams(source.getParams());
  }
}
